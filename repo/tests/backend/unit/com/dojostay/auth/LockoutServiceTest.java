package com.dojostay.auth;

import com.dojostay.audit.AuditService;
import com.dojostay.common.exception.AccountLockedException;
import com.dojostay.users.User;
import com.dojostay.users.UserLockState;
import com.dojostay.users.UserLockStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for LockoutService. Uses Mockito stubs for the JPA repository so the
 * test does not have to track every JpaRepository interface change across Spring Data
 * versions, plus a tiny in-memory map to give the stub realistic save/find semantics.
 */
class LockoutServiceTest {

    private LockoutService service;
    private UserLockStateRepository repo;
    private AuditService auditService;
    private LockoutProperties props;
    private Map<Long, UserLockState> store;

    @BeforeEach
    void setUp() {
        repo = mock(UserLockStateRepository.class);
        auditService = mock(AuditService.class);
        store = new HashMap<>();

        // Wire findById/save against the in-memory map so the service sees realistic state.
        when(repo.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, Long.class))));
        when(repo.save(any(UserLockState.class))).thenAnswer(inv -> {
            UserLockState s = inv.getArgument(0);
            store.put(s.getUserId(), s);
            return s;
        });

        props = new LockoutProperties();
        props.setMaxFailedAttempts(5);
        props.setLockDurationMinutes(15);

        service = new LockoutService(repo, props, auditService);
    }

    @Test
    void recordFailure_increments_counter() {
        User user = userWithId(1L);
        for (int i = 0; i < 4; i++) {
            assertFalse(service.recordFailure(user, "127.0.0.1"));
        }
        assertEquals(4, store.get(1L).getFailedAttempts());
    }

    @Test
    void fifth_failure_locks_account_for_configured_duration() {
        User user = userWithId(2L);
        for (int i = 0; i < 4; i++) {
            service.recordFailure(user, "127.0.0.1");
        }
        boolean lockedNow = service.recordFailure(user, "127.0.0.1");
        assertTrue(lockedNow);

        UserLockState state = store.get(2L);
        assertNotNull(state.getLockedUntil());
        assertTrue(state.getLockedUntil().isAfter(Instant.now()));
        assertTrue(state.getLockedUntil().isBefore(
                Instant.now().plus(props.getLockDurationMinutes() + 1, ChronoUnit.MINUTES)));

        // Audit was called at least once with AUTH_ACCOUNT_LOCKED.
        verify(auditService, atLeastOnce())
                .record(eq(com.dojostay.audit.AuditAction.AUTH_ACCOUNT_LOCKED),
                        eq(2L), eq("user2"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void assertNotLocked_throws_while_locked() {
        User user = userWithId(3L);
        for (int i = 0; i < 5; i++) {
            service.recordFailure(user, "127.0.0.1");
        }
        assertThrows(AccountLockedException.class, () -> service.assertNotLocked(user));
    }

    @Test
    void recordSuccess_resets_counter_and_clears_lock() {
        User user = userWithId(4L);
        service.recordFailure(user, "127.0.0.1");
        service.recordFailure(user, "127.0.0.1");
        service.recordSuccess(user);
        UserLockState state = store.get(4L);
        assertEquals(0, state.getFailedAttempts());
        assertNull(state.getLockedUntil());
    }

    @Test
    void admin_unlock_clears_state_and_records_actor() {
        User user = userWithId(5L);
        for (int i = 0; i < 5; i++) {
            service.recordFailure(user, "127.0.0.1");
        }
        User admin = userWithId(99L);
        service.adminUnlock(user, admin, "127.0.0.1");

        UserLockState state = store.get(5L);
        assertEquals(0, state.getFailedAttempts());
        assertNull(state.getLockedUntil());
        assertEquals(99L, state.getLastUnlockedBy());
        assertNotNull(state.getLastUnlockedAt());

        ArgumentCaptor<com.dojostay.audit.AuditAction> action = ArgumentCaptor.forClass(com.dojostay.audit.AuditAction.class);
        verify(auditService, atLeastOnce())
                .record(action.capture(), eq(99L), eq("user99"), anyString(), anyString(), anyString(), anyString());
        assertTrue(action.getAllValues().contains(com.dojostay.audit.AuditAction.AUTH_ACCOUNT_UNLOCKED));
    }

    @Test
    void expired_lock_resets_counter_on_next_failure() {
        User user = userWithId(6L);
        // Pre-seed an expired lock with a high counter directly into the store.
        UserLockState seed = new UserLockState();
        seed.setUserId(6L);
        seed.setFailedAttempts(5);
        seed.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        store.put(6L, seed);

        boolean lockedNow = service.recordFailure(user, "127.0.0.1");
        assertFalse(lockedNow, "Counter should reset, single failure should not re-lock");
        assertEquals(1, store.get(6L).getFailedAttempts());
    }

    private static User userWithId(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        return u;
    }
}
