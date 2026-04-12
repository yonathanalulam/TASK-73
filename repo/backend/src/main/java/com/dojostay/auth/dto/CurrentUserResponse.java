package com.dojostay.auth.dto;

import com.dojostay.roles.UserRoleType;

import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String username,
        String fullName,
        UserRoleType primaryRole,
        Set<String> roles,
        Set<String> permissions
) {
}
