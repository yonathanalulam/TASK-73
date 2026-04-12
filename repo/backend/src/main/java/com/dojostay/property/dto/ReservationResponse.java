package com.dojostay.property.dto;

import com.dojostay.property.LodgingReservation;

import java.time.Instant;
import java.time.LocalDate;

public record ReservationResponse(
        Long id,
        Long organizationId,
        Long bedId,
        Long studentId,
        String guestName,
        LocalDate startsOn,
        LocalDate endsOn,
        LodgingReservation.Status status,
        String notes,
        Instant createdAt,
        Instant cancelledAt
) {
}
