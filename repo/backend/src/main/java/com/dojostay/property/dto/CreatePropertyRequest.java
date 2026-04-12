package com.dojostay.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePropertyRequest(
        @NotNull Long organizationId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 255) String address,
        @Size(max = 2000) String description,
        @Size(max = 4000) String policies
) {
}
