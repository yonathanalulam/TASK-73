package com.dojostay.credentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Form payload for submitting a credential review. Bound from multipart form
 * parts when an evidence file is attached; also accepted as JSON for the
 * legacy evidence-text-only flow, so clients that cannot upload (early mobile
 * builds, integration bots) still have a working path.
 */
public record SubmitCredentialReviewRequest(
        @NotNull Long studentId,
        @NotBlank @Size(max = 64) String discipline,
        @NotBlank @Size(max = 64) String requestedRank,
        @Size(max = 64) String currentRank,
        @Size(max = 2000) String evidence
) {
}
