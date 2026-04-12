package com.dojostay.training.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        @NotNull Long trainingSessionId,
        @NotNull Long studentId,
        @Size(max = 512) String notes
) {
}
