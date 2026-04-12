package com.dojostay.organizations.dto;

import java.time.Instant;

public record OrganizationResponse(
        Long id,
        String code,
        String name,
        Long parentId,
        boolean active,
        String contactEmail,
        String contactPhone,
        String description,
        Instant createdAt
) {
}
