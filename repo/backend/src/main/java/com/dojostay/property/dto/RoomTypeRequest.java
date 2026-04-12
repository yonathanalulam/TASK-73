package com.dojostay.property.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RoomTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 2000) String description,
        @Min(1) int maxOccupancy,
        @PositiveOrZero int baseRateCents,
        @Size(max = 1000) String features
) {
}
