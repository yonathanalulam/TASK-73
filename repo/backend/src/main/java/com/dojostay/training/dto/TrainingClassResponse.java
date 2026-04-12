package com.dojostay.training.dto;

import java.time.Instant;

public record TrainingClassResponse(
        Long id,
        Long organizationId,
        String code,
        String name,
        String discipline,
        String level,
        int defaultCapacity,
        String description,
        boolean active,
        Instant createdAt
) {
}
