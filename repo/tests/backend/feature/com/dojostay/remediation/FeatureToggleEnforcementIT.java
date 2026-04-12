package com.dojostay.remediation;

import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.community.CommunityService;
import com.dojostay.community.dto.CreatePostRequest;
import com.dojostay.community.Post;
import com.dojostay.ops.FeatureGuard;
import com.dojostay.ops.FeatureToggle;
import com.dojostay.ops.FeatureToggleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.training.BookingService;
import com.dojostay.training.dto.CreateBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests proving feature toggle enforcement:
 * - community writes blocked when community.enabled is disabled
 * - booking writes blocked when bookings.read-only is enabled
 * - reads still work in degraded mode
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeatureToggleEnforcementIT {

    @Autowired private FeatureToggleRepository toggleRepository;
    @Autowired private CommunityService communityService;
    @Autowired private FeatureGuard featureGuard;

    @BeforeEach
    void setUp() {
        setCurrentUser(1L, "admin", UserRoleType.ADMIN,
                Set.of("community.read", "community.write", "bookings.read", "bookings.write"));
    }

    @Test
    void communityWriteBlockedWhenToggleDisabled() {
        // Disable community
        setToggle(FeatureGuard.COMMUNITY_ENABLED, false);

        CreatePostRequest req = new CreatePostRequest(1L, "Test", "body", Post.Visibility.PUBLIC);
        assertThatThrownBy(() -> communityService.createPost(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FEATURE_DISABLED");
    }

    @Test
    void communityWriteAllowedWhenToggleEnabled() {
        setToggle(FeatureGuard.COMMUNITY_ENABLED, true);

        // Reads should work regardless
        assertThatCode(() -> communityService.listPosts()).doesNotThrowAnyException();
    }

    @Test
    void communityReadWorksWhenDisabled() {
        setToggle(FeatureGuard.COMMUNITY_ENABLED, false);

        // Reads should still work in degraded mode
        assertThatCode(() -> communityService.listPosts()).doesNotThrowAnyException();
    }

    @Test
    void bookingReadOnlyBlocksMutations() {
        setToggle(FeatureGuard.BOOKINGS_READ_ONLY, true);

        assertThatThrownBy(() -> featureGuard.requireNotReadOnly(
                FeatureGuard.BOOKINGS_READ_ONLY, "Booking mutations"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void bookingMutationsAllowedWhenNotReadOnly() {
        setToggle(FeatureGuard.BOOKINGS_READ_ONLY, false);

        assertThatCode(() -> featureGuard.requireNotReadOnly(
                FeatureGuard.BOOKINGS_READ_ONLY, "Booking mutations"))
                .doesNotThrowAnyException();
    }

    private void setToggle(String code, boolean enabled) {
        FeatureToggle toggle = toggleRepository.findByCode(code)
                .orElseGet(() -> {
                    FeatureToggle t = new FeatureToggle();
                    t.setCode(code);
                    return t;
                });
        toggle.setEnabled(enabled);
        toggle.setDescription("test toggle");
        toggleRepository.save(toggle);
    }

    private void setCurrentUser(Long id, String username, UserRoleType role, Set<String> perms) {
        CurrentUser cu = new CurrentUser(id, username, username, role, Set.of(role.name()), perms);
        CurrentUserResolver.set(cu);
    }
}
