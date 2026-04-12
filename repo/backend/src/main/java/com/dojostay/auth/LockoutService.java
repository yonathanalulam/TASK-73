package com.dojostay.auth;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.common.exception.AccountLockedException;
import com.dojostay.users.User;
import com.dojostay.users.UserLockState;
import com.dojostay.users.UserLockStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tracks failed login counters per user, locks the account after the configured threshold,
 * and exposes admin-driven unlock.
 *
 * <p>Behavior is fully driven by {@link LockoutProperties} — defaults are 5 failures /
 * 15 minutes per the spec.
 */
@Service
public class LockoutService {

    private final UserLockStateRepository lockRepo;
    private final LockoutProperties props;
    private final AuditService auditService;

    public LockoutService(UserLockStateRepository lockRepo,
                          LockoutProperties props,
                          AuditService auditService) {
        this.lockRepo = lockRepo;
        this.props = props;
        this.auditService = auditService;
    }

    /**
     * Throws {@link AccountLockedException} if the user is currently locked out.
     */
    @Transactional(readOnly = true)
    public void assertNotLocked(User user) {
        UserLockState state = lockRepo.findById(user.getId()).orElse(null);
        if (state != null && state.isCurrentlyLocked()) {
            throw new AccountLockedException("Account is temporarily locked. Try again later.");
        }
    }

    /**
     * Records a failed login. Returns true if this failure caused the account to be locked.
     */
    @Transactional
    public boolean recordFailure(User user, String sourceIp) {
        UserLockState state = lockRepo.findById(user.getId()).orElseGet(() -> {
            UserLockState s = new UserLockState();
            s.setUserId(user.getId());
            return s;
        });

        // If a previous lock has expired, reset the counter before counting this failure.
        if (state.getLockedUntil() != null && state.getLockedUntil().isBefore(Instant.now())) {
            state.setFailedAttempts(0);
            state.setLockedUntil(null);
        }

        state.setFailedAttempts(state.getFailedAttempts() + 1);
        state.setLastFailedAt(Instant.now());

        boolean nowLocked = false;
        if (state.getFailedAttempts() >= props.getMaxFailedAttempts()) {
            state.setLockedUntil(Instant.now().plus(props.getLockDurationMinutes(), ChronoUnit.MINUTES));
            nowLocked = true;
        }
        lockRepo.save(state);

        if (nowLocked) {
            auditService.record(
                    AuditAction.AUTH_ACCOUNT_LOCKED,
                    user.getId(),
                    user.getUsername(),
                    "USER",
                    String.valueOf(user.getId()),
                    "Account locked after " + state.getFailedAttempts() + " failed attempts",
                    sourceIp
            );
        }
        return nowLocked;
    }

    /**
     * Resets the failure counter on a successful login.
     */
    @Transactional
    public void recordSuccess(User user) {
        lockRepo.findById(user.getId()).ifPresent(state -> {
            state.setFailedAttempts(0);
            state.setLockedUntil(null);
            lockRepo.save(state);
        });
    }

    /**
     * Admin-initiated unlock. Audited as a privileged operation.
     */
    @Transactional
    public void adminUnlock(User target, User actor, String sourceIp) {
        UserLockState state = lockRepo.findById(target.getId()).orElseGet(() -> {
            UserLockState s = new UserLockState();
            s.setUserId(target.getId());
            return s;
        });
        state.setFailedAttempts(0);
        state.setLockedUntil(null);
        state.setLastUnlockedBy(actor.getId());
        state.setLastUnlockedAt(Instant.now());
        lockRepo.save(state);

        auditService.record(
                AuditAction.AUTH_ACCOUNT_UNLOCKED,
                actor.getId(),
                actor.getUsername(),
                "USER",
                String.valueOf(target.getId()),
                "Account unlocked by admin " + actor.getUsername(),
                sourceIp
        );
    }
}
