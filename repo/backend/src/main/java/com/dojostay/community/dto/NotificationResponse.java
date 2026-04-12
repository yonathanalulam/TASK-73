package com.dojostay.community.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long recipientUserId,
        Long organizationId,
        String kind,
        String title,
        String body,
        String referenceType,
        String referenceId,
        Instant readAt,
        Instant createdAt
) {
}
