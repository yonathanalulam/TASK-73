package com.dojostay.credentials.dto;

import com.dojostay.credentials.CredentialReview;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideCredentialReviewRequest(
        @NotNull CredentialReview.Status decision,
        @Size(max = 1000) String reviewNotes
) {
}
