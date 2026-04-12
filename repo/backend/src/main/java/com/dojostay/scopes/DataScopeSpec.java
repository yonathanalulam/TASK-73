package com.dojostay.scopes;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

/**
 * Generic JPA Specification helpers that translate an {@link EffectiveScope} into a
 * query predicate for any entity {@code T} that carries {@code organizationId},
 * {@code departmentId}, or {@code facilityAreaId} fields.
 *
 * <p>Use like:
 * <pre>{@code
 *   Specification<User> scopeSpec = DataScopeSpec.byOrganization("organizationId", scope);
 *   Page<User> page = userRepository.findAll(scopeSpec.and(otherFilters), pageable);
 * }</pre>
 *
 * <p>Semantics:
 * <ul>
 *   <li>If the scope has {@code fullAccess == true}, returns a "no restriction" spec
 *       (null predicate).</li>
 *   <li>If the scope set is non-empty, filters to {@code field IN (ids)}.</li>
 *   <li>If the scope set is empty, returns a "never match" spec so unscoped non-admin
 *       users can never see anything. This is a deliberate deny-by-default posture.</li>
 * </ul>
 */
public final class DataScopeSpec {

    private DataScopeSpec() {
    }

    public static <T> Specification<T> byOrganization(String field, EffectiveScope scope) {
        return byField(field, scope.fullAccess(), scope.organizationIds());
    }

    public static <T> Specification<T> byDepartment(String field, EffectiveScope scope) {
        return byField(field, scope.fullAccess(), scope.departmentIds());
    }

    public static <T> Specification<T> byFacilityArea(String field, EffectiveScope scope) {
        return byField(field, scope.fullAccess(), scope.facilityAreaIds());
    }

    /**
     * Compound scope specification: filters by organization AND optionally narrows
     * by department and facility area when the scope includes those axes. This is
     * the preferred entry point for entities that carry all three FK columns.
     *
     * <p>Semantics: org filter is always applied. If the scope also has department
     * or facility area ids, those are applied as additional AND constraints on the
     * corresponding entity fields. If the scope has no entries for a sub-org axis,
     * that axis is not filtered (the org-level grant is sufficient).
     */
    public static <T> Specification<T> byFullScope(
            String orgField, String deptField, String areaField, EffectiveScope scope) {
        Specification<T> spec = byOrganization(orgField, scope);
        if (!scope.fullAccess() && scope.departmentIds() != null && !scope.departmentIds().isEmpty()) {
            spec = spec.and(byDepartment(deptField, scope));
        }
        if (!scope.fullAccess() && scope.facilityAreaIds() != null && !scope.facilityAreaIds().isEmpty()) {
            spec = spec.and(byFacilityArea(areaField, scope));
        }
        return spec;
    }

    private static <T> Specification<T> byField(String field, boolean fullAccess, Set<Long> ids) {
        return (root, query, cb) -> {
            if (fullAccess) {
                return cb.conjunction(); // always true
            }
            if (ids == null || ids.isEmpty()) {
                return cb.disjunction(); // always false — deny by default
            }
            Path<Long> path = root.get(field);
            return path.in(ids);
        };
    }
}
