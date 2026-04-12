package com.dojostay.training;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.training.dto.CreateTrainingClassRequest;
import com.dojostay.training.dto.CreateTrainingSessionRequest;
import com.dojostay.training.dto.TrainingClassResponse;
import com.dojostay.training.dto.TrainingSessionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Training-side of Phase 5: manages reusable class templates and the scheduled
 * session instances that students book. Read paths are scope-filtered. Writes
 * emit {@link AuditAction#TRAINING_SESSION_CREATED} and
 * {@link AuditAction#TRAINING_SESSION_CANCELLED} events.
 */
@Service
public class TrainingService {

    private final TrainingClassRepository classRepository;
    private final TrainingSessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public TrainingService(TrainingClassRepository classRepository,
                           TrainingSessionRepository sessionRepository,
                           BookingRepository bookingRepository,
                           DataScopeService dataScopeService,
                           AuditService auditService) {
        this.classRepository = classRepository;
        this.sessionRepository = sessionRepository;
        this.bookingRepository = bookingRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TrainingClassResponse> listClasses() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        return classRepository.findAll().stream()
                .filter(c -> scope.fullAccess() || scope.hasOrganization(c.getOrganizationId()))
                .map(TrainingService::toClassResponse)
                .toList();
    }

    @Transactional
    public TrainingClassResponse createClass(CreateTrainingClassRequest req, String sourceIp) {
        assertOrgAccessible(req.organizationId());
        classRepository.findByOrganizationIdAndCode(req.organizationId(), req.code()).ifPresent(c -> {
            throw new BusinessException("DUPLICATE_CLASS_CODE",
                    "A training class with that code already exists in this organization",
                    HttpStatus.CONFLICT);
        });
        TrainingClass tc = new TrainingClass();
        tc.setOrganizationId(req.organizationId());
        tc.setCode(req.code());
        tc.setName(req.name());
        tc.setDiscipline(req.discipline());
        tc.setLevel(req.level());
        tc.setDefaultCapacity(req.defaultCapacity());
        tc.setDescription(req.description());
        TrainingClass saved = classRepository.save(tc);

        auditService.record(AuditAction.TRAINING_SESSION_CREATED, actorId(), actorUsername(),
                "TRAINING_CLASS", String.valueOf(saved.getId()),
                "Training class created: " + saved.getCode(), sourceIp);
        return toClassResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResponse> listSessionsForClass(Long trainingClassId) {
        TrainingClass tc = loadAccessibleClass(trainingClassId);
        return sessionRepository.findByTrainingClassId(tc.getId()).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional
    public TrainingSessionResponse createSession(CreateTrainingSessionRequest req, String sourceIp) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BusinessException("INVALID_RANGE",
                    "endsAt must be strictly after startsAt", HttpStatus.BAD_REQUEST);
        }
        // Phase B6: enforce 30-minute slot alignment. Both endpoints must land on
        // a :00 / :30 boundary and the duration must be a positive multiple of
        // 30 minutes. We measure from epoch so the grid is DST-agnostic.
        assertSlotAligned(req.startsAt(), "startsAt");
        assertSlotAligned(req.endsAt(), "endsAt");
        long durationMinutes = ChronoUnit.MINUTES.between(req.startsAt(), req.endsAt());
        if (durationMinutes <= 0 || durationMinutes % 30 != 0) {
            throw new BusinessException("INVALID_SLOT_DURATION",
                    "Session duration must be a positive multiple of 30 minutes",
                    HttpStatus.BAD_REQUEST);
        }
        if (req.sessionType() == TrainingSession.SessionType.ONLINE
                && (req.onlineUrl() == null || req.onlineUrl().isBlank())) {
            throw new BusinessException("ONLINE_URL_REQUIRED",
                    "Online sessions must include an onlineUrl",
                    HttpStatus.BAD_REQUEST);
        }
        TrainingClass tc = loadAccessibleClass(req.trainingClassId());
        TrainingSession s = new TrainingSession();
        s.setTrainingClassId(tc.getId());
        s.setOrganizationId(tc.getOrganizationId());
        s.setStartsAt(req.startsAt());
        s.setEndsAt(req.endsAt());
        s.setLocation(req.location());
        s.setInstructorUserId(req.instructorUserId());
        s.setCapacity(req.capacity() != null ? req.capacity() : tc.getDefaultCapacity());
        s.setNotes(req.notes());
        s.setStatus(TrainingSession.Status.SCHEDULED);
        s.setSessionType(req.sessionType());
        s.setOnlineUrl(req.onlineUrl());
        s.setLevel(req.level());
        s.setWeightClassLbs(req.weightClassLbs());
        s.setStyle(req.style());
        TrainingSession saved = sessionRepository.save(s);

        auditService.record(AuditAction.TRAINING_SESSION_CREATED, actorId(), actorUsername(),
                "TRAINING_SESSION", String.valueOf(saved.getId()),
                "Session scheduled at " + saved.getStartsAt(), sourceIp);
        return toSessionResponse(saved);
    }

    /**
     * Reject anything not on a :00 or :30 clock boundary. We work in whole
     * epoch-minutes so the grid is insensitive to time-zone offsets.
     */
    private static void assertSlotAligned(Instant t, String fieldName) {
        long epochMinutes = t.getEpochSecond() / 60;
        if (t.getEpochSecond() % 60 != 0 || epochMinutes % 30 != 0) {
            throw new BusinessException("INVALID_SLOT_ALIGNMENT",
                    fieldName + " must fall on a 30-minute boundary (HH:00 or HH:30)",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Phase B6: filter listing for sparring matching. All predicates are
     * optional; any combination narrows the result set. Scope is applied first
     * so filters only operate on rows the caller can already see.
     */
    @Transactional(readOnly = true)
    public List<TrainingSessionResponse> filterSessions(
            TrainingSession.SessionType sessionType,
            String level,
            Integer weightClassLbs,
            String style,
            Instant from,
            Instant to) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        return sessionRepository.findAll().stream()
                .filter(s -> scope.fullAccess() || scope.hasOrganization(s.getOrganizationId()))
                .filter(s -> scope.fullAccess()
                        || scope.departmentIds().isEmpty()
                        || s.getDepartmentId() == null
                        || scope.hasDepartment(s.getDepartmentId()))
                .filter(s -> sessionType == null || sessionType == s.getSessionType())
                .filter(s -> level == null || level.equalsIgnoreCase(s.getLevel()))
                .filter(s -> weightClassLbs == null
                        || (s.getWeightClassLbs() != null
                                && s.getWeightClassLbs().equals(weightClassLbs)))
                .filter(s -> style == null || style.equalsIgnoreCase(s.getStyle()))
                .filter(s -> from == null || !s.getStartsAt().isBefore(from))
                .filter(s -> to == null || !s.getEndsAt().isAfter(to))
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional
    public TrainingSessionResponse cancelSession(Long sessionId, String sourceIp) {
        TrainingSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Training session not found"));
        assertOrgAccessibleRead(s.getOrganizationId(), "Training session not found");
        if (s.getStatus() == TrainingSession.Status.CANCELLED) {
            return toSessionResponse(s);
        }
        s.setStatus(TrainingSession.Status.CANCELLED);
        s.setCancelledAt(Instant.now());
        TrainingSession saved = sessionRepository.save(s);
        auditService.record(AuditAction.TRAINING_SESSION_CANCELLED, actorId(), actorUsername(),
                "TRAINING_SESSION", String.valueOf(saved.getId()),
                "Training session cancelled", sourceIp);
        return toSessionResponse(saved);
    }

    TrainingSession loadAccessibleSession(Long sessionId) {
        TrainingSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Training session not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(s.getOrganizationId())) {
            throw new NotFoundException("Training session not found");
        }
        return s;
    }

    private TrainingClass loadAccessibleClass(Long classId) {
        TrainingClass tc = classRepository.findById(classId)
                .orElseThrow(() -> new NotFoundException("Training class not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(tc.getOrganizationId())) {
            throw new NotFoundException("Training class not found");
        }
        return tc;
    }

    private void assertOrgAccessible(Long organizationId) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return;
        if (organizationId == null || !scope.hasOrganization(organizationId)) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Target organization is not in your data scope",
                    HttpStatus.FORBIDDEN);
        }
    }

    private void assertOrgAccessibleRead(Long organizationId, String notFoundMessage) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return;
        if (organizationId == null || !scope.hasOrganization(organizationId)) {
            throw new NotFoundException(notFoundMessage);
        }
    }

    private static TrainingClassResponse toClassResponse(TrainingClass c) {
        return new TrainingClassResponse(
                c.getId(), c.getOrganizationId(), c.getCode(), c.getName(),
                c.getDiscipline(), c.getLevel(), c.getDefaultCapacity(),
                c.getDescription(), c.isActive(), c.getCreatedAt()
        );
    }

    private TrainingSessionResponse toSessionResponse(TrainingSession s) {
        long booked = bookingRepository.countActiveBookingsForSession(s.getId());
        return new TrainingSessionResponse(
                s.getId(), s.getTrainingClassId(), s.getOrganizationId(),
                s.getStartsAt(), s.getEndsAt(), s.getLocation(),
                s.getInstructorUserId(), s.getCapacity(), booked,
                s.getStatus(), s.getNotes(),
                s.getSessionType(), s.getOnlineUrl(), s.getLevel(),
                s.getWeightClassLbs(), s.getStyle(),
                s.getCreatedAt(), s.getCancelledAt()
        );
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }
}
