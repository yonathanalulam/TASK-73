package com.dojostay.training.dto;

import java.time.Instant;

public record CreditTransactionResponse(
        Long id,
        Long studentId,
        Long organizationId,
        int delta,
        int balanceAfter,
        String reason,
        String referenceType,
        String referenceId,
        String notes,
        Instant createdAt
) {
}
