package com.dojostay.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        Long parentId,
        @Email @Size(max = 190) String contactEmail,
        @Size(max = 32) String contactPhone,
        @Size(max = 512) String description
) {
}
