package com.dojostay.ops.dto;

import com.dojostay.ops.OpsJobRecord;
import jakarta.validation.constraints.NotNull;

public record TriggerOpsJobRequest(
        @NotNull OpsJobRecord.JobKind jobKind
) {}
