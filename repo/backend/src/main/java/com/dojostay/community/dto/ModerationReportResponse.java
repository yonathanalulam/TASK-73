package com.dojostay.community.dto;

import com.dojostay.community.ModerationReport;

import java.time.Instant;

public record ModerationReportResponse(
        Long id,
        Long organizationId,
        Long reporterUserId,
        ModerationReport.TargetType targetType,
        Long targetId,
        String reason,
        String details,
        ModerationReport.Status status,
        String resolutionNotes,
        Long reviewedByUserId,
        Instant createdAt,
        Instant resolvedAt
) {
}
