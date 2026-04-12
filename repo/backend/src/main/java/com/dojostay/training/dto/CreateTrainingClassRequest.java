package com.dojostay.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTrainingClassRequest(
        @NotNull Long organizationId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 64) String discipline,
        @Size(max = 32) String level,
        @Positive int defaultCapacity,
        @Size(max = 512) String description
) {
}
