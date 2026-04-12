package com.dojostay.training;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.ops.FeatureGuard;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.training.dto.BookingResponse;
import com.dojostay.training.dto.CreateBookingRequest;
import com.dojostay.training.dto.CreditAdjustmentRequest;
import com.dojostay.training.dto.CreditTransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Books students into training sessions and enforces three invariants:
 *
 * <ol>
 *   <li>The session must not be cancelled.</li>
 *   <li>Capacity must not be exceeded.</li>
 *   <li>The student must not already have a non-terminal booking that overlaps
 *       the target session's time window (prevents double-booking across
 *       concurrent classes).</li>
 * </ol>
 *
 * <p>Phase B6 lifecycle: bookings start in {@link Booking.Status#INITIATED}
 * when first created. A reviewer (or the self-service flow) transitions them
 * through {@link #confirm(Long, String) confirm},
 * {@link #cancel(Long, String) cancel}, and
 * {@link #refund(Long, int, String, String) refund}. Legal transitions:
 *
 * <pre>
 *   INITIATED → CONFIRMED
 *   INITIATED → CANCELED
 *   CONFIRMED → CANCELED
 *   CANCELED  → REFUNDED   (posts credit-ledger entry)
 * </pre>
 *
 * Refunds are never cash — only internal credit. The refund call creates a
 * {@link CreditTransaction} via {@link CreditService#adjust} and stores the
 * resulting tx id on the booking. Scope enforcement delegates to the
 * session's parent organization.
 */
@Service
public class BookingService {

    /** Non-terminal states from which a booking can be cancelled. */
    private static final Set<Booking.Status> CANCELABLE =
            EnumSet.of(Booking.Status.INITIATED, Booking.Status.CONFIRMED,
                    Booking.Status.BOOKED, Booking.Status.CHECKED_IN);

    /** Terminal states that release the seat. */
    private static final Set<Booking.Status> SEAT_RELEASED =
            EnumSet.of(Booking.Status.CANCELED, Booking.Status.CANCELLED,
                    Booking.Status.REFUNDED, Booking.Status.NO_SHOW);

    private final BookingRepository bookingRepository;
    private final TrainingSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final DataScopeService dataScopeService;
    private final FeatureGuard featureGuard;
    private final AuditService auditService;
    private final CreditService creditService;

    public BookingService(BookingRepository bookingRepository,
                          TrainingSessionRepository sessionRepository,
                          StudentRepository studentRepository,
                          DataScopeService dataScopeService,
                          FeatureGuard featureGuard,
                          AuditService auditService,
                          CreditService creditService) {
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.dataScopeService = dataScopeService;
        this.featureGuard = featureGuard;
        this.auditService = auditService;
        this.creditService = creditService;
    }

    private void requireBookingWritable() {
        featureGuard.requireNotReadOnly(FeatureGuard.BOOKINGS_READ_ONLY, "Booking mutations");
    }

    /**
     * List bookings for the student linked to the current authenticated user.
     * Falls back to empty list if the user has no linked student record.
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> listForCurrentUser() {
        Long userId = actorId();
        if (userId == null) return List.of();
        return studentRepository.findByUserId(userId)
                .map(student -> bookingRepository.findByStudentId(student.getId()).stream()
                        .map(BookingService::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForSession(Long sessionId) {
        TrainingSession s = loadAccessibleSession(sessionId);
        return bookingRepository.findByTrainingSessionId(s.getId()).stream()
                .map(BookingService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForStudent(Long studentId) {
        Student student = loadAccessibleStudent(studentId);
        return bookingRepository.findByStudentId(student.getId()).stream()
                .map(BookingService::toResponse)
                .toList();
    }

    @Transactional
    public BookingResponse book(CreateBookingRequest req, String sourceIp) {
        requireBookingWritable();
        TrainingSession session = loadAccessibleSession(req.trainingSessionId());
        Student student = loadAccessibleStudent(req.studentId());

        if (!session.getOrganizationId().equals(student.getOrganizationId())) {
            throw new BusinessException("ORG_MISMATCH",
                    "Student and training session belong to different organizations",
                    HttpStatus.BAD_REQUEST);
        }
        if (session.getStatus() == TrainingSession.Status.CANCELLED) {
            throw new BusinessException("SESSION_CANCELLED",
                    "Session has been cancelled and cannot accept bookings",
                    HttpStatus.CONFLICT);
        }
        long booked = bookingRepository.countActiveBookingsForSession(session.getId());
        if (booked >= session.getCapacity()) {
            throw new BusinessException("SESSION_FULL",
                    "Session has no remaining seats",
                    HttpStatus.CONFLICT);
        }
        List<Booking> conflicts = bookingRepository.findConflictingBookings(
                student.getId(), session.getStartsAt(), session.getEndsAt());
        if (!conflicts.isEmpty()) {
            throw new BusinessException("BOOKING_CONFLICT",
                    "Student already has an overlapping booking",
                    HttpStatus.CONFLICT);
        }

        Booking b = new Booking();
        b.setTrainingSessionId(session.getId());
        b.setStudentId(student.getId());
        b.setOrganizationId(session.getOrganizationId());
        b.setStatus(Booking.Status.INITIATED);
        // Copy session type onto the booking so reporting / client views don't
        // need a second JOIN just to tell a venue booking from an online one.
        if (session.getSessionType() != null) {
            b.setSessionType(Booking.SessionType.valueOf(session.getSessionType().name()));
        }
        b.setNotes(req.notes());
        b.setCreatedByUserId(actorId() != null ? actorId() : 0L);
        Booking saved = bookingRepository.save(b);

        auditService.record(AuditAction.BOOKING_CREATED, actorId(), actorUsername(),
                "BOOKING", String.valueOf(saved.getId()),
                "Student " + student.getId() + " initiated booking on session " + session.getId(),
                sourceIp);
        return toResponse(saved);
    }

    /**
     * Move an {@code INITIATED} booking to {@code CONFIRMED}. No-op for rows
     * that are already confirmed. Rejects any other origin state so the
     * state machine cannot be bypassed.
     */
    @Transactional
    public BookingResponse confirm(Long bookingId, String sourceIp) {
        requireBookingWritable();
        Booking b = loadAccessibleBooking(bookingId);
        if (b.getStatus() == Booking.Status.CONFIRMED) {
            return toResponse(b);
        }
        if (b.getStatus() != Booking.Status.INITIATED) {
            throw new BusinessException("INVALID_TRANSITION",
                    "Only INITIATED bookings can be confirmed (current=" + b.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }
        b.setStatus(Booking.Status.CONFIRMED);
        Booking saved = bookingRepository.save(b);
        auditService.record(AuditAction.BOOKING_CREATED, actorId(), actorUsername(),
                "BOOKING", String.valueOf(saved.getId()),
                "Booking confirmed", sourceIp);
        return toResponse(saved);
    }

    /**
     * Cancel a booking. Idempotent for already-terminal rows. The status
     * written depends on the origin row: legacy rows preserve the old
     * {@code CANCELLED} spelling so history stays consistent; B6 rows use
     * the new {@code CANCELED} spelling.
     */
    @Transactional
    public BookingResponse cancel(Long bookingId, String sourceIp) {
        requireBookingWritable();
        Booking b = loadAccessibleBooking(bookingId);
        if (SEAT_RELEASED.contains(b.getStatus())) {
            return toResponse(b);
        }
        if (!CANCELABLE.contains(b.getStatus())) {
            throw new BusinessException("INVALID_TRANSITION",
                    "Booking in state " + b.getStatus() + " cannot be cancelled",
                    HttpStatus.CONFLICT);
        }
        Booking.Status target = (b.getStatus() == Booking.Status.INITIATED
                || b.getStatus() == Booking.Status.CONFIRMED)
                ? Booking.Status.CANCELED
                : Booking.Status.CANCELLED;
        b.setStatus(target);
        b.setCancelledAt(Instant.now());
        Booking saved = bookingRepository.save(b);
        auditService.record(AuditAction.BOOKING_CANCELLED, actorId(), actorUsername(),
                "BOOKING", String.valueOf(saved.getId()),
                "Booking cancelled -> " + target, sourceIp);
        return toResponse(saved);
    }

    /**
     * Post an internal-credit refund for a cancelled booking and move it to
     * {@code REFUNDED}. Refunds are NEVER cash — the ledger delta is positive
     * credit posted via {@link CreditService#adjust}. The booking stores the
     * resulting ledger tx id so audit can trace the refund back.
     *
     * @param creditAmount positive number of credits to refund
     */
    @Transactional
    public BookingResponse refund(Long bookingId, int creditAmount, String notes, String sourceIp) {
        requireBookingWritable();
        if (creditAmount <= 0) {
            throw new BusinessException("INVALID_REFUND_AMOUNT",
                    "Refund amount must be a positive number of credits",
                    HttpStatus.BAD_REQUEST);
        }
        Booking b = loadAccessibleBooking(bookingId);
        if (b.getStatus() == Booking.Status.REFUNDED) {
            return toResponse(b);
        }
        if (b.getStatus() != Booking.Status.CANCELED && b.getStatus() != Booking.Status.CANCELLED) {
            throw new BusinessException("INVALID_TRANSITION",
                    "Only cancelled bookings can be refunded (current=" + b.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }
        if (b.getRefundCreditTxId() != null) {
            throw new BusinessException("ALREADY_REFUNDED",
                    "Booking already has a refund ledger entry",
                    HttpStatus.CONFLICT);
        }

        CreditTransactionResponse tx = creditService.adjust(new CreditAdjustmentRequest(
                b.getStudentId(),
                creditAmount,
                "BOOKING_REFUND",
                "BOOKING",
                String.valueOf(b.getId()),
                notes
        ), sourceIp);

        b.setStatus(Booking.Status.REFUNDED);
        b.setRefundCreditTxId(tx.id());
        Booking saved = bookingRepository.save(b);
        auditService.record(AuditAction.BOOKING_CANCELLED, actorId(), actorUsername(),
                "BOOKING", String.valueOf(saved.getId()),
                "Booking refunded -> credit tx " + tx.id() + " amount=" + creditAmount,
                sourceIp);
        return toResponse(saved);
    }

    private Booking loadAccessibleBooking(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(b.getOrganizationId())) {
            throw new NotFoundException("Booking not found");
        }
        return b;
    }

    private TrainingSession loadAccessibleSession(Long sessionId) {
        TrainingSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Training session not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(s.getOrganizationId())) {
            throw new NotFoundException("Training session not found");
        }
        return s;
    }

    private Student loadAccessibleStudent(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(s.getOrganizationId())) {
            throw new NotFoundException("Student not found");
        }
        return s;
    }

    private static BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getId(), b.getTrainingSessionId(), b.getStudentId(),
                b.getOrganizationId(), b.getStatus(), b.getSessionType(),
                b.getRefundCreditTxId(), b.getNotes(),
                b.getCheckedInAt(), b.getCreatedAt(), b.getCancelledAt()
        );
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }
}
