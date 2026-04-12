package com.dojostay.ops.dto;

import java.time.Instant;

public record FeatureToggleResponse(
        Long id,
        String code,
        String description,
        boolean enabled,
        Instant updatedAt,
        Long updatedByUserId
) {
}
