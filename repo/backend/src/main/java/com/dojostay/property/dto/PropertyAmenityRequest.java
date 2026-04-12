package com.dojostay.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyAmenityRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String label,
        @Size(max = 64) String icon
) {
}
