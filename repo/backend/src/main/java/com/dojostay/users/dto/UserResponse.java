package com.dojostay.users.dto;

import com.dojostay.roles.UserRoleType;

import java.time.Instant;
import java.util.Set;

/**
 * Client-facing representation of a user. Password hashes and lockout state never
 * appear here — those are internal. Exposed via {@code GET /api/users}.
 */
public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        UserRoleType primaryRole,
        boolean enabled,
        Long organizationId,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
