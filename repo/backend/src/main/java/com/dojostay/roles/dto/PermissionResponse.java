package com.dojostay.roles.dto;

public record PermissionResponse(
        Long id,
        String code,
        String displayName,
        String description,
        String resourceCode
) {
}
