package com.dojostay.property.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record NightlyRateRequest(
        @NotNull LocalDate stayDate,
        @PositiveOrZero int rateCents,
        @PositiveOrZero int availableCount
) {
}
