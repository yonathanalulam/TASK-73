package com.dojostay.remediation;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.property.Property;
import com.dojostay.property.PropertyRepository;
import com.dojostay.property.PropertyService;
import com.dojostay.property.dto.PropertyResponse;
import com.dojostay.roles.UserRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D11 — A1 scope leakage test.
 * Verifies that out-of-scope reads return existence-hiding 404s
 * and scoped list endpoints omit cross-org data.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class ScopeLeakageIT {

    private static final Long ORG_A = 501L;
    private static final Long ORG_B = 502L;

    @Autowired private PropertyRepository propertyRepository;
    @Autowired private PropertyService propertyService;

    private Property propA;
    private Property propB;

    @BeforeEach
    void setUp() {
        propA = newProperty(ORG_A, "PROP-A", "Prop A");
        propB = newProperty(ORG_B, "PROP-B", "Prop B");
    }

    @AfterEach
    void tearDown() {
        propertyRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void admin_sees_all_properties() {
        authenticateAdmin();
        List<PropertyResponse> all = propertyService.list();
        assertTrue(all.stream().anyMatch(p -> p.code().equals("PROP-A")));
        assertTrue(all.stream().anyMatch(p -> p.code().equals("PROP-B")));
    }

    @Test
    void scoped_staff_only_sees_own_org_properties() {
        authenticateStaff(ORG_A);
        List<PropertyResponse> visible = propertyService.list();
        assertTrue(visible.stream().allMatch(p -> p.organizationId().equals(ORG_A)));
        assertTrue(visible.stream().noneMatch(p -> p.code().equals("PROP-B")));
    }

    @Test
    void availability_on_out_of_scope_property_returns_not_found() {
        authenticateStaff(ORG_A);
        assertThrows(NotFoundException.class,
                () -> propertyService.availability(propB.getId(),
                        java.time.LocalDate.of(2026, 7, 1),
                        java.time.LocalDate.of(2026, 7, 5)));
    }

    private Property newProperty(Long orgId, String code, String name) {
        Property p = new Property();
        p.setOrganizationId(orgId);
        p.setCode(code);
        p.setName(name);
        p.setActive(true);
        return propertyRepository.save(p);
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("property.read", "property.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("property.read"),
                        new SimpleGrantedAuthority("property.write")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static void authenticateStaff(Long orgId) {
        CurrentUser staff = new CurrentUser(10L, "staff-" + orgId, "Staff",
                UserRoleType.STAFF, Set.of("STAFF"),
                Set.of("property.read", "property.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                staff, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"),
                        new SimpleGrantedAuthority("property.read"),
                        new SimpleGrantedAuthority("property.write")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
