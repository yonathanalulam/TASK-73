package com.dojostay.scopes;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.roles.UserRoleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the effective data scope for a user and manages the data_scope_rules table.
 *
 * <p>Central rule: any user holding the {@code ADMIN} role gets an
 * all-access scope — this is the intentional
 * cross-organization admin bypass. All other users are restricted to the union of
 * their scope rules.
 */
@Service
public class DataScopeService {

    private final DataScopeRuleRepository ruleRepository;
    private final AuditService auditService;

    public DataScopeService(DataScopeRuleRepository ruleRepository, AuditService auditService) {
        this.ruleRepository = ruleRepository;
        this.auditService = auditService;
    }

    /**
     * Computes the effective scope for the currently-authenticated user.
     *
     * <p>If no one is authenticated (e.g. in a background job), this returns
     * {@link EffectiveScope#empty()} — no access.
     */
    @Transactional(readOnly = true)
    public EffectiveScope forCurrentUser() {
        return CurrentUserResolver.current()
                .map(this::forUser)
                .orElseGet(EffectiveScope::empty);
    }

    /**
     * Computes the effective scope for an arbitrary user (e.g. as part of admin UI
     * previews).
     */
    @Transactional(readOnly = true)
    public EffectiveScope forUser(CurrentUser user) {
        if (hasFullAccess(user)) {
            return EffectiveScope.allAccess();
        }
        return readRules(user.id());
    }

    @Transactional(readOnly = true)
    public EffectiveScope forUserId(Long userId, boolean isAdmin) {
        if (isAdmin) {
            return EffectiveScope.allAccess();
        }
        return readRules(userId);
    }

    /**
     * Replaces the full set of scope rules for the target user. Emits a single audit
     * record describing the change.
     */
    @Transactional
    public void replaceRules(Long targetUserId, List<DataScopeRule> newRules, String actorSourceIp) {
        ruleRepository.deleteByUserId(targetUserId);
        for (DataScopeRule rule : newRules) {
            rule.setUserId(targetUserId);
            ruleRepository.save(rule);
        }
        CurrentUser actor = CurrentUserResolver.current().orElse(null);
        auditService.record(
                AuditAction.DATA_SCOPE_CHANGED,
                actor != null ? actor.id() : null,
                actor != null ? actor.username() : "system",
                "USER",
                String.valueOf(targetUserId),
                "Data scope rules replaced; new count=" + newRules.size(),
                actorSourceIp
        );
    }

    public boolean hasFullAccess(CurrentUser user) {
        if (user == null) return false;
        if (user.primaryRole() == UserRoleType.ADMIN) return true;
        return user.roles() != null && user.roles().contains("ADMIN");
    }

    private EffectiveScope readRules(Long userId) {
        List<DataScopeRule> rules = ruleRepository.findByUserId(userId);
        Set<Long> orgs = collect(rules, FacilityScopeType.ORGANIZATION);
        Set<Long> depts = collect(rules, FacilityScopeType.DEPARTMENT);
        Set<Long> areas = collect(rules, FacilityScopeType.FACILITY_AREA);
        return new EffectiveScope(false, orgs, depts, areas);
    }

    private static Set<Long> collect(List<DataScopeRule> rules, FacilityScopeType type) {
        return rules.stream()
                .filter(r -> r.getScopeType() == type)
                .map(DataScopeRule::getScopeTargetId)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
