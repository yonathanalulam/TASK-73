package com.dojostay.ops.dto;

import com.dojostay.ops.ExportJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateExportJobRequest(
        Long organizationId,
        @NotBlank @Size(max = 64) String kind,
        @NotNull ExportJob.Format format
) {
}
