package com.dojostay.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Fields that an admin can change on an existing user via
 * {@code PUT /api/users/{id}}. Role assignment and password changes use dedicated
 * endpoints.
 */
public record UpdateUserRequest(
        @Size(max = 128) String fullName,
        @Email @Size(max = 190) String email,
        Long organizationId,
        Boolean enabled
) {
}
