package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    List<UserBlock> findByUserIdOrderByIdDesc(Long userId);
}
