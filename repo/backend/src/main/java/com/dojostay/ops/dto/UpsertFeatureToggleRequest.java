package com.dojostay.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertFeatureToggleRequest(
        @NotBlank @Size(max = 128) String code,
        @Size(max = 512) String description,
        @NotNull Boolean enabled
) {
}
