package com.dojostay.remediation;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.roles.UserRoleType;
import com.dojostay.students.EnrollmentStatus;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.training.Booking;
import com.dojostay.training.BookingRepository;
import com.dojostay.training.BookingService;
import com.dojostay.training.CreditService;
import com.dojostay.training.CreditTransactionRepository;
import com.dojostay.training.TrainingClass;
import com.dojostay.training.TrainingClassRepository;
import com.dojostay.training.TrainingSession;
import com.dojostay.training.TrainingSessionRepository;
import com.dojostay.training.dto.BookingResponse;
import com.dojostay.training.dto.CreateBookingRequest;
import com.dojostay.training.dto.CreditAdjustmentRequest;
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

/**
 * D11 — B6 booking lifecycle test.
 * Verifies: INITIATED -> CONFIRMED -> CANCELED -> REFUNDED state machine,
 * and invalid transitions are rejected.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class BookingLifecycleIT {

    private static final Long ORG = 601L;

    @Autowired private TrainingClassRepository classRepo;
    @Autowired private TrainingSessionRepository sessionRepo;
    @Autowired private BookingRepository bookingRepo;
    @Autowired private StudentRepository studentRepo;
    @Autowired private CreditTransactionRepository ledgerRepo;
    @Autowired private BookingService bookingService;
    @Autowired private CreditService creditService;

    private TrainingSession session;
    private Student student;

    @BeforeEach
    void setUp() {
        ledgerRepo.deleteAll();
        bookingRepo.deleteAll();
        sessionRepo.deleteAll();
        classRepo.deleteAll();
        studentRepo.deleteAll();

        TrainingClass tc = new TrainingClass();
        tc.setOrganizationId(ORG);
        tc.setCode("BJ-B6");
        tc.setName("BJJ B6 Test");
        tc.setDefaultCapacity(10);
        classRepo.save(tc);

        Instant base = Instant.parse("2026-08-01T10:00:00Z");
        session = new TrainingSession();
        session.setTrainingClassId(tc.getId());
        session.setOrganizationId(ORG);
        session.setStartsAt(base);
        session.setEndsAt(base.plus(1, ChronoUnit.HOURS));
        session.setCapacity(10);
        session.setStatus(TrainingSession.Status.SCHEDULED);
        sessionRepo.save(session);

        student = new Student();
        student.setOrganizationId(ORG);
        student.setFullName("Lifecycle Test Student");
        student.setEnrollmentStatus(EnrollmentStatus.ACTIVE);
        studentRepo.save(student);

        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void full_lifecycle_initiated_to_refunded() {
        // 1. Book -> INITIATED
        BookingResponse booked = bookingService.book(
                new CreateBookingRequest(session.getId(), student.getId(), null), "127.0.0.1");
        assertEquals(Booking.Status.INITIATED, booked.status());

        // 2. Confirm -> CONFIRMED
        BookingResponse confirmed = bookingService.confirm(booked.id(), "127.0.0.1");
        assertEquals(Booking.Status.CONFIRMED, confirmed.status());

        // 3. Cancel -> CANCELED
        BookingResponse canceled = bookingService.cancel(booked.id(), "127.0.0.1");
        assertEquals(Booking.Status.CANCELED, canceled.status());

        // 4. Give credits and refund -> REFUNDED
        creditService.adjust(new CreditAdjustmentRequest(
                student.getId(), 100, "PURCHASE", null, null, "Pre-load"), "127.0.0.1");
        BookingResponse refunded = bookingService.refund(
                booked.id(), 50, "Test refund", "127.0.0.1");
        assertEquals(Booking.Status.REFUNDED, refunded.status());
        assertNotNull(refunded.refundCreditTxId());
    }

    @Test
    void confirm_on_already_confirmed_is_idempotent() {
        BookingResponse booked = bookingService.book(
                new CreateBookingRequest(session.getId(), student.getId(), null), "127.0.0.1");
        bookingService.confirm(booked.id(), "127.0.0.1");
        BookingResponse again = bookingService.confirm(booked.id(), "127.0.0.1");
        assertEquals(Booking.Status.CONFIRMED, again.status());
    }

    @Test
    void refund_without_cancel_is_rejected() {
        BookingResponse booked = bookingService.book(
                new CreateBookingRequest(session.getId(), student.getId(), null), "127.0.0.1");
        bookingService.confirm(booked.id(), "127.0.0.1");

        var ex = assertThrows(BusinessException.class,
                () -> bookingService.refund(booked.id(), 10, "bad", "127.0.0.1"));
        assertEquals("INVALID_TRANSITION", ex.getCode());
    }

    @Test
    void double_refund_is_rejected() {
        BookingResponse booked = bookingService.book(
                new CreateBookingRequest(session.getId(), student.getId(), null), "127.0.0.1");
        bookingService.cancel(booked.id(), "127.0.0.1");

        creditService.adjust(new CreditAdjustmentRequest(
                student.getId(), 200, "PURCHASE", null, null, "top-up"), "127.0.0.1");
        bookingService.refund(booked.id(), 10, "first refund", "127.0.0.1");

        var ex = assertThrows(BusinessException.class,
                () -> bookingService.refund(booked.id(), 10, "second refund", "127.0.0.1"));
        assertEquals("ALREADY_REFUNDED", ex.getCode());
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
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("training.read"),
                        new SimpleGrantedAuthority("training.write"),
                        new SimpleGrantedAuthority("bookings.read"),
                        new SimpleGrantedAuthority("bookings.write"),
                        new SimpleGrantedAuthority("credits.read"),
                        new SimpleGrantedAuthority("credits.write"),
                        new SimpleGrantedAuthority("students.read"),
                        new SimpleGrantedAuthority("students.write")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
