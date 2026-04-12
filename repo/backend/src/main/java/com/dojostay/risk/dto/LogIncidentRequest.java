package com.dojostay.risk.dto;

import com.dojostay.risk.RiskFlag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record LogIncidentRequest(
        @NotNull Long organizationId,
        @NotNull Instant occurredAt,
        RiskFlag.SubjectType subjectType,
        Long subjectId,
        @NotBlank @Size(max = 64) String category,
        @NotNull RiskFlag.Severity severity,
        @NotBlank @Size(max = 2000) String description,
        @Size(max = 2000) String followUp
) {
}
