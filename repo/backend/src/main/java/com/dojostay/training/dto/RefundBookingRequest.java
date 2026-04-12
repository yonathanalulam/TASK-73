package com.dojostay.training.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Refund request for a cancelled booking. Refunds are always internal
 * credit — never cash — so the payload is just the credit amount to post and
 * an optional note that lands on the ledger entry.
 */
public record RefundBookingRequest(
        @NotNull @Positive Integer creditAmount,
        @Size(max = 512) String notes
) {
}
