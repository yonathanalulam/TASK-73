package com.dojostay.community.dto;

import com.dojostay.community.ModerationReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateModerationReportRequest(
        @NotNull ModerationReport.TargetType targetType,
        @NotNull Long targetId,
        @NotBlank @Size(max = 64) String reason,
        @Size(max = 1000) String details
) {
}
