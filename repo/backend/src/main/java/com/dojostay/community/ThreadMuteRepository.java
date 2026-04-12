package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThreadMuteRepository extends JpaRepository<ThreadMute, Long> {

    Optional<ThreadMute> findByUserIdAndPostId(Long userId, Long postId);

    List<ThreadMute> findByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);
}
