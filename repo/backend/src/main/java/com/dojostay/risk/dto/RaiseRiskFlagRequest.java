package com.dojostay.risk.dto;

import com.dojostay.risk.RiskFlag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RaiseRiskFlagRequest(
        @NotNull Long organizationId,
        @NotNull RiskFlag.SubjectType subjectType,
        @NotNull Long subjectId,
        @NotBlank @Size(max = 64) String category,
        @NotNull RiskFlag.Severity severity,
        @Size(max = 1000) String description
) {
}
