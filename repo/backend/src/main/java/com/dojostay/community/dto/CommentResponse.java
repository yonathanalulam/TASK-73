package com.dojostay.community.dto;

import com.dojostay.community.PostComment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long postId,
        Long organizationId,
        Long authorUserId,
        String body,
        Long parentCommentId,
        Long quotedCommentId,
        PostComment.Status status,
        String hiddenReason,
        Instant createdAt,
        Instant hiddenAt,
        Instant restoredAt
) {
}
