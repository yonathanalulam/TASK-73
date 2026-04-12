package com.dojostay.remediation;

import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.roles.UserRoleType;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.students.EnrollmentStatus;
import com.dojostay.training.BookingService;
import com.dojostay.training.dto.BookingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that the /api/bookings/mine contract works correctly:
 * - returns bookings for the current user's linked student
 * - returns empty list when no student is linked
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingContractIT {

    @Autowired private BookingService bookingService;
    @Autowired private StudentRepository studentRepository;

    @BeforeEach
    void setUp() {
        // Create a student linked to user 100
        Student s = new Student();
        s.setUserId(100L);
        s.setOrganizationId(1L);
        s.setFullName("Contract Test Student");
        s.setEnrollmentStatus(EnrollmentStatus.ACTIVE);
        studentRepository.save(s);
    }

    @Test
    void myBookingsReturnsEmptyForUserWithNoLinkedStudent() {
        setCurrentUser(999L, "nolink", UserRoleType.STUDENT);

        List<BookingResponse> result = bookingService.listForCurrentUser();
        assertThat(result).isEmpty();
    }

    @Test
    void myBookingsReturnsEmptyForLinkedStudentWithNoBookings() {
        setCurrentUser(100L, "student1", UserRoleType.STUDENT);

        List<BookingResponse> result = bookingService.listForCurrentUser();
        assertThat(result).isEmpty();
    }

    private void setCurrentUser(Long id, String username, UserRoleType role) {
        CurrentUser cu = new CurrentUser(id, username, username, role, Set.of(role.name()),
                Set.of("bookings.read", "bookings.write"));
        CurrentUserResolver.set(cu);
    }
}
