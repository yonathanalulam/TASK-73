package com.dojostay.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotNull Long postId,
        @NotBlank @Size(max = 2000) String body,
        Long parentCommentId,
        Long quotedCommentId
) {
}
