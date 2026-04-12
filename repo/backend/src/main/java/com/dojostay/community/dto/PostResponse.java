package com.dojostay.community.dto;

import com.dojostay.community.Post;

import java.time.Instant;

public record PostResponse(
        Long id,
        Long organizationId,
        Long authorUserId,
        String title,
        String body,
        Post.Visibility visibility,
        Post.Status status,
        String hiddenReason,
        Instant createdAt,
        Instant updatedAt,
        Instant hiddenAt,
        Instant restoredAt
) {
}
