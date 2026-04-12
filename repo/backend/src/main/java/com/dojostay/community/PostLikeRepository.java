package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findFirstByPostIdAndUserId(Long postId, Long userId);

    Optional<PostLike> findFirstByCommentIdAndUserId(Long commentId, Long userId);

    long countByPostId(Long postId);

    long countByCommentId(Long commentId);

    List<PostLike> findByUserIdOrderByIdDesc(Long userId);
}
