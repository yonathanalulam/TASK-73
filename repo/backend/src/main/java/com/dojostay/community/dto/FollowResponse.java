package com.dojostay.community.dto;

import java.time.Instant;

public record FollowResponse(
        Long id,
        Long followerUserId,
        Long followedUserId,
        Instant createdAt
) {
}
