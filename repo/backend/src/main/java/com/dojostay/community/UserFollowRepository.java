package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    Optional<UserFollow> findByFollowerUserIdAndFollowedUserId(Long followerUserId, Long followedUserId);

    List<UserFollow> findByFollowerUserIdOrderByIdDesc(Long followerUserId);

    List<UserFollow> findByFollowedUserIdOrderByIdDesc(Long followedUserId);

    long countByFollowedUserId(Long followedUserId);

    long countByFollowerUserId(Long followerUserId);
}
