package com.dojostay.ops.dto;

import com.dojostay.ops.BackupStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordBackupRequest(
        @NotBlank @Size(max = 64) String kind,
        @NotNull BackupStatus.Status status,
        @NotNull Instant startedAt,
        Instant completedAt,
        @Size(max = 512) String location,
        Long sizeBytes,
        Long durationMs,
        @Size(max = 1000) String notes
) {
}
