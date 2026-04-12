package com.dojostay.users.dto;

import com.dojostay.roles.UserRoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String fullName,
        @Email @Size(max = 190) String email,
        @NotNull UserRoleType primaryRole,
        Long organizationId,
        @NotBlank String password,
        Set<String> roleCodes
) {
}
