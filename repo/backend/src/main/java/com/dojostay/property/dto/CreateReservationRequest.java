package com.dojostay.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateReservationRequest(
        @NotNull Long bedId,
        @NotBlank @Size(max = 128) String guestName,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        Long studentId,
        @Size(max = 512) String notes
) {
}
