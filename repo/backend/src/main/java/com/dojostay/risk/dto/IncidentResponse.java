package com.dojostay.risk.dto;

import com.dojostay.risk.RiskFlag;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long organizationId,
        Instant occurredAt,
        Long reporterUserId,
        RiskFlag.SubjectType subjectType,
        Long subjectId,
        String category,
        RiskFlag.Severity severity,
        String description,
        String followUp,
        Instant createdAt
) {
}
