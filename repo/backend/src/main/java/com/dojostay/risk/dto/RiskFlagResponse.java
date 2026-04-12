package com.dojostay.risk.dto;

import com.dojostay.risk.RiskFlag;

import java.time.Instant;

public record RiskFlagResponse(
        Long id,
        Long organizationId,
        RiskFlag.SubjectType subjectType,
        Long subjectId,
        String category,
        RiskFlag.Severity severity,
        String description,
        RiskFlag.Status status,
        Long raisedByUserId,
        Long clearedByUserId,
        String clearanceNotes,
        Instant createdAt,
        Instant clearedAt
) {
}
