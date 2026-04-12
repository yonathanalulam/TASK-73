package com.dojostay.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClearRiskFlagRequest(
        @NotBlank @Size(max = 1000) String clearanceNotes
) {
}
