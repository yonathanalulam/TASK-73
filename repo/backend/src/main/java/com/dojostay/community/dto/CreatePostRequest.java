package com.dojostay.community.dto;

import com.dojostay.community.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotNull Long organizationId,
        @Size(max = 160) String title,
        @NotBlank @Size(max = 10_000) String body,
        Post.Visibility visibility
) {
}
