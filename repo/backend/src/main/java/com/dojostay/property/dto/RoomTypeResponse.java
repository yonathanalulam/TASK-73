package com.dojostay.property.dto;

public record RoomTypeResponse(
        Long id,
        Long propertyId,
        String code,
        String name,
        String description,
        int maxOccupancy,
        int baseRateCents,
        String features
) {
}
