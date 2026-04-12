package com.dojostay.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreditAdjustmentRequest(
        @NotNull Long studentId,
        /** Signed: positive grants credits, negative consumes them. Zero is rejected. */
        @NotNull Integer delta,
        @NotBlank @Size(max = 64) String reason,
        @Size(max = 64) String referenceType,
        @Size(max = 64) String referenceId,
        @Size(max = 512) String notes
) {
}
