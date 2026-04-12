package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostMentionRepository extends JpaRepository<PostMention, Long> {

    List<PostMention> findByPostIdOrderByIdAsc(Long postId);

    List<PostMention> findByCommentIdOrderByIdAsc(Long commentId);

    List<PostMention> findByMentionedUserIdOrderByIdDesc(Long mentionedUserId);
}
