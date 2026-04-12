package com.dojostay.property.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        Long propertyId,
        LocalDate startsOn,
        LocalDate endsOn,
        List<BedAvailability> beds
) {
    public record BedAvailability(
            Long bedId,
            String bedLabel,
            Long roomId,
            String roomName,
            boolean available
    ) {
    }
}
