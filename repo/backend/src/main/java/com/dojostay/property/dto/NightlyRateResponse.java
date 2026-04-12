package com.dojostay.property.dto;

import java.time.LocalDate;

public record NightlyRateResponse(
        Long id,
        Long roomTypeId,
        LocalDate stayDate,
        int rateCents,
        int availableCount
) {
}
