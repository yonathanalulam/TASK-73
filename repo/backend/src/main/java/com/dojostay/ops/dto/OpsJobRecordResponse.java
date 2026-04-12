package com.dojostay.ops.dto;

import java.time.Instant;

public record OpsJobRecordResponse(
        Long id,
        String jobKind,
        String status,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        String summary,
        String triggeredBy,
        Instant createdAt
) {}
