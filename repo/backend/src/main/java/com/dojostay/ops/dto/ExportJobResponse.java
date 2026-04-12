package com.dojostay.ops.dto;

import com.dojostay.ops.ExportJob;

import java.time.Instant;

public record ExportJobResponse(
        Long id,
        Long organizationId,
        Long requestedByUserId,
        String kind,
        ExportJob.Status status,
        ExportJob.Format format,
        Integer rowCount,
        String errorMessage,
        String artifactPath,
        String watermarkText,
        Instant createdAt,
        Instant completedAt
) {
}
