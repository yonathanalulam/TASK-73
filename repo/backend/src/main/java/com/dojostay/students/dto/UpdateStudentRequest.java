package com.dojostay.students.dto;

import com.dojostay.students.EnrollmentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateStudentRequest(
        @Size(max = 128) String fullName,
        @Email @Size(max = 190) String email,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        @Size(max = 128) String emergencyContactName,
        @Size(max = 32) String emergencyContactPhone,
        EnrollmentStatus enrollmentStatus,
        @Size(max = 32) String skillLevel,
        @Size(max = 1024) String notes,
        // Phase B5 — organization master data
        @Size(max = 128) String school,
        @Size(max = 128) String program,
        @Size(max = 64) String classGroup,
        @Size(max = 128) String housingAssignment
) {
}
