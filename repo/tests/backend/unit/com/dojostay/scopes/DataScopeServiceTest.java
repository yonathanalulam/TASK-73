package com.dojostay.scopes;

import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.roles.UserRoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link DataScopeService}. Verifies the central rule of Phase 2:
 * admins bypass scope filtering (full access) while every other user is limited
 * to the union of the rows they own in {@code data_scope_rules}.
 */
class DataScopeServiceTest {

    private DataScopeRuleRepository repo;
    private AuditService auditService;
    private DataScopeService service;

    @BeforeEach
    void setUp() {
        repo = mock(DataScopeRuleRepository.class);
        auditService = mock(AuditService.class);
        service = new DataScopeService(repo, auditService);
    }

    @Test
    void admin_by_primary_role_gets_full_access() {
        CurrentUser admin = new CurrentUser(1L, "admin", "Admin", UserRoleType.ADMIN,
                Set.of("ADMIN"), Set.of("users.read", "users.write"));

        EffectiveScope scope = service.forUser(admin);

        assertTrue(scope.fullAccess());
        assertTrue(scope.hasOrganization(999L),
                "Admin should see any organization regardless of rules");
    }

    @Test
    void admin_by_role_code_gets_full_access_even_if_primary_role_is_staff() {
        // Someone whose primary display role is STAFF but who has been granted the
        // ADMIN role should still bypass filtering — the role grant is authoritative.
        CurrentUser elevated = new CurrentUser(2L, "elev", "Elevated", UserRoleType.STAFF,
                Set.of("STAFF", "ADMIN"), Set.of());

        EffectiveScope scope = service.forUser(elevated);

        assertTrue(scope.fullAccess());
    }

    @Test
    void staff_with_no_rules_has_empty_scope() {
        CurrentUser staff = new CurrentUser(3L, "staff", "Staff", UserRoleType.STAFF,
                Set.of("STAFF"), Set.of("users.read"));
        when(repo.findByUserId(3L)).thenReturn(List.of());

        EffectiveScope scope = service.forUser(staff);

        assertFalse(scope.fullAccess());
        assertFalse(scope.hasAnyOrganization());
        assertFalse(scope.hasOrganization(1L));
    }

    @Test
    void staff_with_rules_gets_union_across_axes() {
        CurrentUser staff = new CurrentUser(4L, "staff", "Staff", UserRoleType.STAFF,
                Set.of("STAFF"), Set.of("users.read"));
        when(repo.findByUserId(4L)).thenReturn(List.of(
                rule(FacilityScopeType.ORGANIZATION, 10L),
                rule(FacilityScopeType.ORGANIZATION, 11L),
                rule(FacilityScopeType.DEPARTMENT, 100L),
                rule(FacilityScopeType.FACILITY_AREA, 1000L)
        ));

        EffectiveScope scope = service.forUser(staff);

        assertFalse(scope.fullAccess());
        assertEquals(Set.of(10L, 11L), scope.organizationIds());
        assertEquals(Set.of(100L), scope.departmentIds());
        assertEquals(Set.of(1000L), scope.facilityAreaIds());

        assertTrue(scope.hasOrganization(10L));
        assertTrue(scope.hasOrganization(11L));
        assertFalse(scope.hasOrganization(12L), "Orgs not in rules must be denied");
    }

    @Test
    void forUserId_isAdmin_flag_short_circuits_repository() {
        EffectiveScope scope = service.forUserId(5L, true);
        assertTrue(scope.fullAccess());
    }

    @Test
    void replaceRules_deletes_existing_and_saves_new_with_audit() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DataScopeRule rule = rule(FacilityScopeType.ORGANIZATION, 42L);
        service.replaceRules(7L, List.of(rule), "127.0.0.1");

        org.mockito.Mockito.verify(repo).deleteByUserId(7L);
        org.mockito.Mockito.verify(repo).save(rule);
        assertEquals(7L, rule.getUserId(), "Rule should be re-parented to the target user");
        org.mockito.Mockito.verify(auditService).record(
                org.mockito.ArgumentMatchers.eq(com.dojostay.audit.AuditAction.DATA_SCOPE_CHANGED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq("7"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("127.0.0.1")
        );
    }

    @Test
    void department_scoped_user_sees_only_department_ids() {
        CurrentUser staff = new CurrentUser(5L, "dept-staff", "Dept Staff", UserRoleType.STAFF,
                Set.of("STAFF"), Set.of("users.read"));
        when(repo.findByUserId(5L)).thenReturn(List.of(
                rule(FacilityScopeType.ORGANIZATION, 10L),
                rule(FacilityScopeType.DEPARTMENT, 200L)
        ));

        EffectiveScope scope = service.forUser(staff);

        assertFalse(scope.fullAccess());
        assertTrue(scope.hasOrganization(10L));
        assertTrue(scope.hasDepartment(200L));
        assertFalse(scope.hasDepartment(201L), "Departments not in rules must be denied");
    }

    @Test
    void facility_area_scoped_user_sees_only_assigned_areas() {
        CurrentUser staff = new CurrentUser(6L, "area-staff", "Area Staff", UserRoleType.STAFF,
                Set.of("STAFF"), Set.of("users.read"));
        when(repo.findByUserId(6L)).thenReturn(List.of(
                rule(FacilityScopeType.ORGANIZATION, 10L),
                rule(FacilityScopeType.FACILITY_AREA, 3000L),
                rule(FacilityScopeType.FACILITY_AREA, 3001L)
        ));

        EffectiveScope scope = service.forUser(staff);

        assertFalse(scope.fullAccess());
        assertTrue(scope.hasFacilityArea(3000L));
        assertTrue(scope.hasFacilityArea(3001L));
        assertFalse(scope.hasFacilityArea(3002L), "Areas not in rules must be denied");
    }

    private static DataScopeRule rule(FacilityScopeType type, long targetId) {
        DataScopeRule r = new DataScopeRule();
        r.setScopeType(type);
        r.setScopeTargetId(targetId);
        return r;
    }
}
