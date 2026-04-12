package com.dojostay.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Directed follow relationship — {@code followerUserId} subscribed to updates
 * from {@code followedUserId}. Unfollow deletes the row.
 */
@Entity
@Table(name = "user_follows")
public class UserFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_user_id", nullable = false)
    private Long followerUserId;

    @Column(name = "followed_user_id", nullable = false)
    private Long followedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getFollowerUserId() { return followerUserId; }
    public void setFollowerUserId(Long followerUserId) { this.followerUserId = followerUserId; }
    public Long getFollowedUserId() { return followedUserId; }
    public void setFollowedUserId(Long followedUserId) { this.followedUserId = followedUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
