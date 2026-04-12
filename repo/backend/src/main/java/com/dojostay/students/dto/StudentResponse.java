package com.dojostay.students.dto;

import com.dojostay.students.EnrollmentStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Student read DTO. The sensitive string fields — {@code externalId},
 * {@code email}, {@code phone}, {@code emergencyContactPhone} — are masked by
 * default. Callers holding the {@code students.view-raw} permission see them
 * unmasked via the service layer (see {@code StudentService.toResponse}).
 */
public record StudentResponse(
        Long id,
        Long userId,
        Long organizationId,
        String externalId,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String emergencyContactName,
        String emergencyContactPhone,
        EnrollmentStatus enrollmentStatus,
        String skillLevel,
        String notes,
        String school,
        String program,
        String classGroup,
        String housingAssignment,
        Instant enrolledAt,
        Instant createdAt,
        Instant updatedAt,
        /** True when sensitive fields in this response are masked. */
        boolean masked
) {
}
