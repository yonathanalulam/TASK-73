package com.dojostay.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_lock_states")
public class UserLockState {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "last_unlocked_by")
    private Long lastUnlockedBy;

    @Column(name = "last_unlocked_at")
    private Instant lastUnlockedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public Instant getLastFailedAt() { return lastFailedAt; }
    public void setLastFailedAt(Instant lastFailedAt) { this.lastFailedAt = lastFailedAt; }

    public Long getLastUnlockedBy() { return lastUnlockedBy; }
    public void setLastUnlockedBy(Long lastUnlockedBy) { this.lastUnlockedBy = lastUnlockedBy; }

    public Instant getLastUnlockedAt() { return lastUnlockedAt; }
    public void setLastUnlockedAt(Instant lastUnlockedAt) { this.lastUnlockedAt = lastUnlockedAt; }

    public boolean isCurrentlyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
