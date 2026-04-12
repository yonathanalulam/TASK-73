package com.dojostay.community.dto;

public record LikeStatusResponse(
        Long postId,
        Long commentId,
        long likeCount,
        boolean likedByMe
) {
}
