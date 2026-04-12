package com.dojostay.training;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.roles.UserRoleType;
import com.dojostay.students.EnrollmentStatus;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.training.dto.BookingResponse;
import com.dojostay.training.dto.CreateBookingRequest;
import com.dojostay.training.dto.CreditAdjustmentRequest;
import com.dojostay.training.dto.CreditBalanceResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 guarantees:
 *
 * <ol>
 *   <li>Bookings reject a student who already has an overlapping non-cancelled
 *       booking ({@code BOOKING_CONFLICT}).</li>
 *   <li>Bookings reject once a session reaches capacity ({@code SESSION_FULL}).</li>
 *   <li>Credit ledger refuses to go negative and updates {@code balanceAfter}
 *       deterministically on each row.</li>
 * </ol>
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class BookingConflictIT {

    private static final Long ORG_A = 700L;

    @Autowired private TrainingClassRepository classRepository;
    @Autowired private TrainingSessionRepository sessionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CreditTransactionRepository ledgerRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TrainingService trainingService;
    @Autowired private BookingService bookingService;
    @Autowired private CreditService creditService;

    private TrainingClass trainingClass;
    private TrainingSession morning;
    private TrainingSession overlapping;
    private TrainingSession afternoon;
    private Student alice;
    private Student bob;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        classRepository.deleteAll();
        studentRepository.deleteAll();

        trainingClass = new TrainingClass();
        trainingClass.setOrganizationId(ORG_A);
        trainingClass.setCode("JJ-ADULT");
        trainingClass.setName("Adult Jiu-Jitsu");
        trainingClass.setDefaultCapacity(1);
        classRepository.save(trainingClass);

        Instant base = Instant.parse("2026-06-01T09:00:00Z");
        morning = newSession(base, base.plus(1, ChronoUnit.HOURS), 2);
        overlapping = newSession(base.plus(30, ChronoUnit.MINUTES), base.plus(90, ChronoUnit.MINUTES), 5);
        afternoon = newSession(base.plus(5, ChronoUnit.HOURS), base.plus(6, ChronoUnit.HOURS), 5);

        alice = newStudent("Alice");
        bob = newStudent("Bob");

        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overlapping_booking_for_same_student_is_rejected() {
        BookingResponse first = bookingService.book(
                new CreateBookingRequest(morning.getId(), alice.getId(), null), "127.0.0.1");
        assertNotNull(first.id());

        var ex = assertThrows(BusinessException.class, () ->
                bookingService.book(
                        new CreateBookingRequest(overlapping.getId(), alice.getId(), null),
                        "127.0.0.1"));
        assertEquals("BOOKING_CONFLICT", ex.getCode());

        // Same student can still book a non-overlapping session later in the day.
        BookingResponse later = bookingService.book(
                new CreateBookingRequest(afternoon.getId(), alice.getId(), null), "127.0.0.1");
        assertNotNull(later.id());

        // A different student is unaffected by Alice's first booking.
        BookingResponse bobMorning = bookingService.book(
                new CreateBookingRequest(morning.getId(), bob.getId(), null), "127.0.0.1");
        assertNotNull(bobMorning.id());
    }

    @Test
    void session_rejects_booking_past_capacity() {
        // morning session capacity is 2 — fill it and then hit the limit.
        bookingService.book(new CreateBookingRequest(morning.getId(), alice.getId(), null), "127.0.0.1");
        bookingService.book(new CreateBookingRequest(morning.getId(), bob.getId(), null), "127.0.0.1");

        Student charlie = newStudent("Charlie");
        var ex = assertThrows(BusinessException.class, () ->
                bookingService.book(
                        new CreateBookingRequest(morning.getId(), charlie.getId(), null),
                        "127.0.0.1"));
        assertEquals("SESSION_FULL", ex.getCode());
    }

    @Test
    void credit_ledger_tracks_running_balance_and_refuses_to_go_negative() {
        creditService.adjust(new CreditAdjustmentRequest(
                alice.getId(), 10, "PURCHASE", null, null, "10 class pack"), "127.0.0.1");
        creditService.adjust(new CreditAdjustmentRequest(
                alice.getId(), -3, "BOOKING", "BOOKING", "123", null), "127.0.0.1");

        CreditBalanceResponse balance = creditService.getBalance(alice.getId());
        assertEquals(7, balance.balance());

        List<?> history = creditService.history(alice.getId());
        assertEquals(2, history.size());

        var ex = assertThrows(BusinessException.class, () ->
                creditService.adjust(new CreditAdjustmentRequest(
                        alice.getId(), -20, "BOOKING", null, null, null), "127.0.0.1"));
        assertEquals("INSUFFICIENT_CREDIT", ex.getCode());

        // Zero delta rejected outright.
        var zeroEx = assertThrows(BusinessException.class, () ->
                creditService.adjust(new CreditAdjustmentRequest(
                        alice.getId(), 0, "ADJUSTMENT", null, null, null), "127.0.0.1"));
        assertEquals("ZERO_DELTA", zeroEx.getCode());

        // Balance unchanged after the two rejections.
        assertEquals(7, creditService.getBalance(alice.getId()).balance());
        assertTrue(ledgerRepository.findByStudentIdOrderByIdDesc(alice.getId()).size() == 2);
    }

    private TrainingSession newSession(Instant startsAt, Instant endsAt, int capacity) {
        TrainingSession s = new TrainingSession();
        s.setTrainingClassId(trainingClass.getId());
        s.setOrganizationId(ORG_A);
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setCapacity(capacity);
        s.setStatus(TrainingSession.Status.SCHEDULED);
        return sessionRepository.save(s);
    }

    private Student newStudent(String name) {
        Student s = new Student();
        s.setOrganizationId(ORG_A);
        s.setFullName(name);
        s.setEnrollmentStatus(EnrollmentStatus.ACTIVE);
        return studentRepository.save(s);
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("training.read", "training.write",
                        "bookings.read", "bookings.write",
                        "credits.read", "credits.write",
                        "students.read", "students.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("training.read"),
                        new SimpleGrantedAuthority("training.write"),
                        new SimpleGrantedAuthority("bookings.read"),
                        new SimpleGrantedAuthority("bookings.write"),
                        new SimpleGrantedAuthority("credits.read"),
                        new SimpleGrantedAuthority("credits.write"),
                        new SimpleGrantedAuthority("students.read"),
                        new SimpleGrantedAuthority("students.write")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
