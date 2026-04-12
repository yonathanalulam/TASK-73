package com.dojostay.students.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateStudentRequest(
        @NotNull Long organizationId,
        @Size(max = 64) String externalId,
        @NotBlank @Size(max = 128) String fullName,
        @Email @Size(max = 190) String email,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        @Size(max = 128) String emergencyContactName,
        @Size(max = 32) String emergencyContactPhone,
        @Size(max = 32) String skillLevel,
        @Size(max = 1024) String notes,
        Long userId,
        // Phase B5 — organization master data
        @Size(max = 128) String school,
        @Size(max = 128) String program,
        @Size(max = 64) String classGroup,
        @Size(max = 128) String housingAssignment
) {
}
