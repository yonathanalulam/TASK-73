package com.dojostay.community.dto;

import com.dojostay.community.ModerationReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @NotNull ModerationReport.Status resolution,
        @Size(max = 1000) String resolutionNotes,
        @Size(max = 255) String hiddenReason,
        boolean hideTarget
) {
}
