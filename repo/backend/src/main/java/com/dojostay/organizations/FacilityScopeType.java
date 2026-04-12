package com.dojostay.organizations;

/**
 * Axis along which a data scope rule restricts a user.
 *
 * <p>The order is hierarchical from broadest to narrowest: an ORGANIZATION scope implies
 * access to all departments and facility areas beneath it, a DEPARTMENT scope implies
 * access to facility areas beneath it, and FACILITY_AREA is the narrowest.
 */
public enum FacilityScopeType {
    ORGANIZATION,
    DEPARTMENT,
    FACILITY_AREA
}
