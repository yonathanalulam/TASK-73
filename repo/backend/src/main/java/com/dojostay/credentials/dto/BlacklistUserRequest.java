package com.dojostay.credentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for the "blacklist the student behind a credential review" action.
 * Reason is required because every blacklist row must be attributable in the
 * audit log — the free-form reason is stored verbatim on the user and copied
 * into the audit entry.
 */
public record BlacklistUserRequest(
        @NotBlank @Size(max = 512) String reason
) {
}
