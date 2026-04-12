package com.dojostay.remediation;

import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.community.CommunityService;
import com.dojostay.ops.FeatureGuard;
import com.dojostay.ops.FeatureToggle;
import com.dojostay.ops.FeatureToggleRepository;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRule;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests proving cross-org social graph operations respect scope:
 * - user can follow/block in-scope user
 * - user cannot follow/block out-of-scope user
 * - admin can follow/block anyone
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrossOrgSocialGraphIT {

    @Autowired private CommunityService communityService;
    @Autowired private UserRepository userRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private FeatureToggleRepository toggleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User userInOrg10;
    private User userInOrg20;

    @BeforeEach
    void setUp() {
        // Ensure community is enabled
        FeatureToggle toggle = toggleRepository.findByCode(FeatureGuard.COMMUNITY_ENABLED)
                .orElseGet(() -> {
                    FeatureToggle t = new FeatureToggle();
                    t.setCode(FeatureGuard.COMMUNITY_ENABLED);
                    return t;
                });
        toggle.setEnabled(true);
        toggle.setDescription("test");
        toggleRepository.save(toggle);

        // Create two users in different orgs
        userInOrg10 = createUser("user_org10", 10L);
        userInOrg20 = createUser("user_org20", 20L);
    }

    @Test
    void userCanFollowInScopeUser() {
        // Actor is scoped to org 10, target is also in org 10
        User actor = createUser("actor_follow", 10L);
        grantScope(actor.getId(), 10L);
        setCurrentUser(actor.getId(), actor.getUsername(), UserRoleType.STAFF);

        assertThatCode(() -> communityService.follow(userInOrg10.getId(), "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void userCannotFollowOutOfScopeUser() {
        // Actor is scoped to org 10, target is in org 20
        User actor = createUser("actor_nofollow", 10L);
        grantScope(actor.getId(), 10L);
        setCurrentUser(actor.getId(), actor.getUsername(), UserRoleType.STAFF);

        assertThatThrownBy(() -> communityService.follow(userInOrg20.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OUT_OF_SCOPE");
    }

    @Test
    void userCannotBlockOutOfScopeUser() {
        User actor = createUser("actor_noblock", 10L);
        grantScope(actor.getId(), 10L);
        setCurrentUser(actor.getId(), actor.getUsername(), UserRoleType.STAFF);

        assertThatThrownBy(() -> communityService.blockUser(userInOrg20.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OUT_OF_SCOPE");
    }

    @Test
    void adminCanFollowAnyUser() {
        setCurrentUser(1L, "admin", UserRoleType.ADMIN);

        assertThatCode(() -> communityService.follow(userInOrg20.getId(), "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    private User createUser(String username, Long orgId) {
        User u = new User();
        u.setUsername(username);
        u.setFullName(username);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setPrimaryRole(UserRoleType.STAFF);
        u.setOrganizationId(orgId);
        u.setEnabled(true);
        return userRepository.save(u);
    }

    private void grantScope(Long userId, Long orgId) {
        DataScopeRule rule = new DataScopeRule();
        rule.setUserId(userId);
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(orgId);
        scopeRuleRepository.save(rule);
    }

    private void setCurrentUser(Long id, String username, UserRoleType role) {
        CurrentUser cu = new CurrentUser(id, username, username, role, Set.of(role.name()),
                Set.of("community.read", "community.write"));
        CurrentUserResolver.set(cu);
    }
}
