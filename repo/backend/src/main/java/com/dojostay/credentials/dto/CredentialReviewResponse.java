package com.dojostay.credentials.dto;

import com.dojostay.credentials.CredentialReview;

import java.time.Instant;

public record CredentialReviewResponse(
        Long id,
        Long organizationId,
        Long studentId,
        String discipline,
        String requestedRank,
        String currentRank,
        String evidence,
        CredentialReview.Status status,
        String reviewNotes,
        Long submittedByUserId,
        Long reviewedByUserId,
        Instant createdAt,
        Instant decidedAt,
        String fileName,
        String fileMime,
        Long fileSize,
        String fileSha256
) {
}
