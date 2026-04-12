package com.dojostay.remediation;

import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRule;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests proving scope rule management:
 * - admin can manage scope rules (replace)
 * - scoped user can access in-scope records
 * - scoped user cannot access out-of-scope records
 * - unscoped non-admin users are safely restricted (empty scope)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScopeManagementIT {

    @Autowired private DataScopeService dataScopeService;
    @Autowired private DataScopeRuleRepository ruleRepository;

    @BeforeEach
    void setUp() {
        // Clean up any existing rules for test user
        ruleRepository.deleteByUserId(500L);
    }

    @Test
    void adminCanReplaceScopeRules() {
        setCurrentUser(1L, "admin", UserRoleType.ADMIN);

        DataScopeRule rule = new DataScopeRule();
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(10L);

        dataScopeService.replaceRules(500L, List.of(rule), "127.0.0.1");

        List<DataScopeRule> rules = ruleRepository.findByUserId(500L);
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getScopeType()).isEqualTo(FacilityScopeType.ORGANIZATION);
        assertThat(rules.get(0).getScopeTargetId()).isEqualTo(10L);
    }

    @Test
    void scopedUserCanAccessInScopeRecords() {
        // Grant user 500 access to org 10
        DataScopeRule rule = new DataScopeRule();
        rule.setUserId(500L);
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(10L);
        ruleRepository.save(rule);

        setCurrentUser(500L, "staff1", UserRoleType.STAFF);
        EffectiveScope scope = dataScopeService.forCurrentUser();

        assertThat(scope.fullAccess()).isFalse();
        assertThat(scope.hasOrganization(10L)).isTrue();
    }

    @Test
    void scopedUserCannotAccessOutOfScopeRecords() {
        DataScopeRule rule = new DataScopeRule();
        rule.setUserId(500L);
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(10L);
        ruleRepository.save(rule);

        setCurrentUser(500L, "staff1", UserRoleType.STAFF);
        EffectiveScope scope = dataScopeService.forCurrentUser();

        assertThat(scope.hasOrganization(99L)).isFalse();
    }

    @Test
    void unscopedNonAdminHasEmptyScope() {
        // No rules assigned to user 600
        setCurrentUser(600L, "newuser", UserRoleType.STUDENT);
        EffectiveScope scope = dataScopeService.forCurrentUser();

        assertThat(scope.fullAccess()).isFalse();
        assertThat(scope.hasAnyOrganization()).isFalse();
    }

    @Test
    void adminBypassesScopeRestrictions() {
        setCurrentUser(1L, "admin", UserRoleType.ADMIN);
        EffectiveScope scope = dataScopeService.forCurrentUser();

        assertThat(scope.fullAccess()).isTrue();
        assertThat(scope.hasOrganization(999L)).isTrue();
    }

    private void setCurrentUser(Long id, String username, UserRoleType role) {
        CurrentUser cu = new CurrentUser(id, username, username, role, Set.of(role.name()), Set.of());
        CurrentUserResolver.set(cu);
    }
}
