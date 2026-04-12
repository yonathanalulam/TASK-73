package com.dojostay.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Convenience accessor for the {@link CurrentUser} stored in the security context.
 */
public final class CurrentUserResolver {

    private CurrentUserResolver() {
    }

    public static Optional<CurrentUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CurrentUser cu) {
            return Optional.of(cu);
        }
        return Optional.empty();
    }

    /**
     * Sets the current user in the security context. Primarily intended for
     * tests and internal service calls that need to impersonate a user.
     */
    public static void set(CurrentUser user) {
        var authorities = user.permissions() != null
                ? user.permissions().stream().map(SimpleGrantedAuthority::new).toList()
                : java.util.List.<SimpleGrantedAuthority>of();
        var token = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    /** Clears the security context. */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
