package com.dojostay.property.dto;

public record PropertyAmenityResponse(
        Long id,
        Long propertyId,
        String code,
        String label,
        String icon
) {
}
