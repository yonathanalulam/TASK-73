package com.dojostay.ops.dto;

import com.dojostay.ops.BackupStatus;

import java.time.Instant;

public record BackupStatusResponse(
        Long id,
        String kind,
        BackupStatus.Status status,
        String location,
        Long sizeBytes,
        Long durationMs,
        String notes,
        Instant startedAt,
        Instant completedAt
) {
}
