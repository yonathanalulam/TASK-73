package com.dojostay.training.dto;

import com.dojostay.training.Booking;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long trainingSessionId,
        Long studentId,
        Long organizationId,
        Booking.Status status,
        Booking.SessionType sessionType,
        Long refundCreditTxId,
        String notes,
        Instant checkedInAt,
        Instant createdAt,
        Instant cancelledAt
) {
}
