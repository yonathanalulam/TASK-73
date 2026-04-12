package com.dojostay.users.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AssignRolesRequest(
        @NotNull Set<String> roleCodes
) {
}
