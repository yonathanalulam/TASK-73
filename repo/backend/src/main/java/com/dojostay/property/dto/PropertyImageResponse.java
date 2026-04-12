package com.dojostay.property.dto;

public record PropertyImageResponse(
        Long id,
        Long propertyId,
        String storagePath,
        String caption,
        int displayOrder
) {
}
