package com.dojostay.roles.dto;

import java.util.Set;

/**
 * Read-only role representation returned by {@code GET /api/roles}. The permission
 * set is expanded to codes so clients can render the permission matrix without a
 * second request.
 */
public record RoleResponse(
        Long id,
        String code,
        String displayName,
        String description,
        Set<String> permissions
) {
}
