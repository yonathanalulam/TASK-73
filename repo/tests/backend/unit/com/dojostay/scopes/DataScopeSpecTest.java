package com.dojostay.scopes;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for {@link DataScopeSpec} compound scope specification.
 * Verifies that the spec construction doesn't throw and produces non-null
 * specifications for various scope configurations.
 */
class DataScopeSpecTest {

    @Test
    void byFullScope_with_full_access_returns_spec() {
        EffectiveScope scope = EffectiveScope.allAccess();
        Specification<?> spec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        assertNotNull(spec);
    }

    @Test
    void byFullScope_with_empty_scope_returns_spec() {
        EffectiveScope scope = EffectiveScope.empty();
        Specification<?> spec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        assertNotNull(spec);
    }

    @Test
    void byFullScope_with_org_only_returns_spec() {
        EffectiveScope scope = new EffectiveScope(false, Set.of(1L), Set.of(), Set.of());
        Specification<?> spec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        assertNotNull(spec);
    }

    @Test
    void byFullScope_with_org_and_department_returns_compound_spec() {
        EffectiveScope scope = new EffectiveScope(false, Set.of(1L), Set.of(100L), Set.of());
        Specification<?> spec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        assertNotNull(spec, "Compound spec with dept filter must not be null");
    }

    @Test
    void byFullScope_with_all_axes_returns_compound_spec() {
        EffectiveScope scope = new EffectiveScope(false, Set.of(1L), Set.of(100L), Set.of(1000L));
        Specification<?> spec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        assertNotNull(spec, "Compound spec with all axes must not be null");
    }

    @Test
    void byDepartment_with_ids_returns_spec() {
        EffectiveScope scope = new EffectiveScope(false, Set.of(1L), Set.of(100L, 101L), Set.of());
        Specification<?> spec = DataScopeSpec.byDepartment("departmentId", scope);
        assertNotNull(spec);
    }

    @Test
    void byFacilityArea_with_ids_returns_spec() {
        EffectiveScope scope = new EffectiveScope(false, Set.of(1L), Set.of(), Set.of(500L));
        Specification<?> spec = DataScopeSpec.byFacilityArea("facilityAreaId", scope);
        assertNotNull(spec);
    }
}
