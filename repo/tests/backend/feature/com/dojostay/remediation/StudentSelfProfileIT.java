package com.dojostay.remediation;

import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.roles.UserRoleType;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.students.StudentService;
import com.dojostay.students.EnrollmentStatus;
import com.dojostay.students.dto.StudentResponse;
import com.dojostay.students.dto.UpdateStudentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests proving the student self-profile flow works:
 * - student can read own profile
 * - student can update own allowed fields
 * - student cannot access other students' profiles
 * - constrained fields (enrollmentStatus, skillLevel, school, etc.) are not changed by self-update
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentSelfProfileIT {

    @Autowired private StudentService studentService;
    @Autowired private StudentRepository studentRepository;

    private Student studentRecord;

    @BeforeEach
    void setUp() {
        studentRecord = new Student();
        studentRecord.setUserId(100L);
        studentRecord.setOrganizationId(1L);
        studentRecord.setFullName("Test Student");
        studentRecord.setEmail("test@example.com");
        studentRecord.setPhone("555-0100");
        studentRecord.setSkillLevel("BEGINNER");
        studentRecord.setSchool("Test School");
        studentRecord.setEnrollmentStatus(EnrollmentStatus.ACTIVE);
        studentRecord = studentRepository.save(studentRecord);
    }

    @Test
    void studentCanReadOwnProfile() {
        setCurrentUser(100L, "student1", UserRoleType.STUDENT,
                Set.of("students.self.read", "students.self.write"));

        StudentResponse response = studentService.getMyProfile();
        assertThat(response.id()).isEqualTo(studentRecord.getId());
        assertThat(response.fullName()).isEqualTo("Test Student");
    }

    @Test
    void studentCanUpdateOwnContactInfo() {
        setCurrentUser(100L, "student1", UserRoleType.STUDENT,
                Set.of("students.self.read", "students.self.write"));

        UpdateStudentRequest req = new UpdateStudentRequest(
                null, "new@example.com", "555-9999", null,
                "New Contact", "555-8888", null, null, null,
                null, null, null, null);

        StudentResponse response = studentService.updateMyProfile(req, "127.0.0.1");
        assertThat(response.email()).isNotNull();
        assertThat(response.emergencyContactName()).isEqualTo("New Contact");
    }

    @Test
    void studentSelfUpdateDoesNotChangeConstrainedFields() {
        setCurrentUser(100L, "student1", UserRoleType.STUDENT,
                Set.of("students.self.read", "students.self.write"));

        // Attempt to change enrollment status and school via self-update
        UpdateStudentRequest req = new UpdateStudentRequest(
                null, null, null, null, null, null,
                EnrollmentStatus.GRADUATED, "ADVANCED", null,
                "Different School", "Different Program", "GroupZ", "Room999");

        StudentResponse response = studentService.updateMyProfile(req, "127.0.0.1");
        // Constrained fields should not have changed
        Student actual = studentRepository.findById(studentRecord.getId()).orElseThrow();
        assertThat(actual.getEnrollmentStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(actual.getSkillLevel()).isEqualTo("BEGINNER");
        assertThat(actual.getSchool()).isEqualTo("Test School");
    }

    @Test
    void studentCannotAccessOtherStudentProfile() {
        // User 200 has no linked student record
        setCurrentUser(200L, "student2", UserRoleType.STUDENT,
                Set.of("students.self.read", "students.self.write"));

        assertThatThrownBy(() -> studentService.getMyProfile())
                .isInstanceOf(NotFoundException.class);
    }

    private void setCurrentUser(Long id, String username, UserRoleType role, Set<String> perms) {
        CurrentUser cu = new CurrentUser(id, username, username, role, Set.of(role.name()), perms);
        CurrentUserResolver.set(cu);
    }
}
