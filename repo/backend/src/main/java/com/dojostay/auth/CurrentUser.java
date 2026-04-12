package com.dojostay.auth;

import com.dojostay.roles.UserRoleType;

import java.util.Set;

/**
 * Lightweight representation of the authenticated principal that gets stored in the
 * security context. We avoid putting the full JPA entity in the session to keep the
 * session payload small and detach it from Hibernate state.
 */
public record CurrentUser(
        Long id,
        String username,
        String fullName,
        UserRoleType primaryRole,
        Set<String> roles,
        Set<String> permissions
) {
}
