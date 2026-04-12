package com.dojostay.scopes;

import java.util.Set;

/**
 * Immutable snapshot of what a given user is allowed to see, decomposed by scope axis.
 *
 * <ul>
 *   <li>{@code fullAccess == true} → the user bypasses data scope filtering entirely
 *       (typically because they hold the ADMIN role).</li>
 *   <li>Otherwise the three sets are the union of scope target ids the user has access
 *       to at each axis. An empty set at a given axis means "no access at this axis".</li>
 * </ul>
 */
public record EffectiveScope(
        boolean fullAccess,
        Set<Long> organizationIds,
        Set<Long> departmentIds,
        Set<Long> facilityAreaIds
) {

    public static EffectiveScope allAccess() {
        return new EffectiveScope(true, Set.of(), Set.of(), Set.of());
    }

    public static EffectiveScope empty() {
        return new EffectiveScope(false, Set.of(), Set.of(), Set.of());
    }

    public boolean hasOrganization(Long orgId) {
        return fullAccess || (orgId != null && organizationIds.contains(orgId));
    }

    public boolean hasDepartment(Long deptId) {
        return fullAccess || (deptId != null && departmentIds.contains(deptId));
    }

    public boolean hasFacilityArea(Long areaId) {
        return fullAccess || (areaId != null && facilityAreaIds.contains(areaId));
    }

    public boolean hasAnyOrganization() {
        return fullAccess || !organizationIds.isEmpty();
    }
}
