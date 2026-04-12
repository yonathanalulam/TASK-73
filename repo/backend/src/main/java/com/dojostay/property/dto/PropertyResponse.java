package com.dojostay.property.dto;

import java.time.Instant;

public record PropertyResponse(
        Long id,
        Long organizationId,
        String code,
        String name,
        String address,
        String description,
        String policies,
        boolean active,
        Instant createdAt
) {
}
