package com.dojostay.users;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRule;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.users.dto.UserResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature-level proof that {@link UserService#list} enforces data scope at the
 * repository layer. We seed three users across two organizations, then:
 *
 * <ol>
 *   <li>Authenticate as an admin and assert they see everyone.</li>
 *   <li>Authenticate as a staff member scoped to org A and assert they only
 *       see org-A users — the org-B user must not appear in the page.</li>
 * </ol>
 *
 * <p>This is the Phase 2 guarantee: scope filtering lives in the repository
 * query via {@code DataScopeSpec}, so a controller that forgets to call a
 * specific "filter" helper cannot leak cross-org data.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class UserScopeFilteringIT {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long orgAId;
    private Long orgBId;
    private Long adminUserId;
    private Long staffUserId;

    @BeforeEach
    void setUp() {
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = saveRole("ADMIN", "Administrator");
        Role staffRole = saveRole("STAFF", "Staff");

        // Fictional org ids — we bypass the organizations table here because the
        // scope filter only cares about the organization_id column on users.
        orgAId = 100L;
        orgBId = 200L;

        User admin = newUser("root-admin", "Root Admin", UserRoleType.ADMIN, null, adminRole);
        userRepository.save(admin);
        adminUserId = admin.getId();

        User staffA = newUser("staff-a", "Staff A", UserRoleType.STAFF, orgAId, staffRole);
        userRepository.save(staffA);
        staffUserId = staffA.getId();

        User memberA = newUser("member-a", "Member A", UserRoleType.STUDENT, orgAId, staffRole);
        userRepository.save(memberA);

        User memberB = newUser("member-b", "Member B", UserRoleType.STUDENT, orgBId, staffRole);
        userRepository.save(memberB);

        // Grant staffA visibility into org A only.
        DataScopeRule rule = new DataScopeRule();
        rule.setUserId(staffUserId);
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(orgAId);
        scopeRuleRepository.save(rule);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void admin_sees_every_user_across_organizations() {
        authenticateAs(new CurrentUser(
                adminUserId, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("users.read", "users.write")
        ), "ROLE_ADMIN", "users.read");

        Page<UserResponse> page = userService.list(PageRequest.of(0, 50));

        Set<String> usernames = page.getContent().stream()
                .map(UserResponse::username)
                .collect(Collectors.toSet());
        assertTrue(usernames.contains("root-admin"));
        assertTrue(usernames.contains("staff-a"));
        assertTrue(usernames.contains("member-a"));
        assertTrue(usernames.contains("member-b"),
                "Admin must see org-B users — no scope filtering applies");
        assertEquals(4, usernames.size());
    }

    @Test
    void staff_scoped_to_org_a_cannot_see_org_b_users() {
        authenticateAs(new CurrentUser(
                staffUserId, "staff-a", "Staff A",
                UserRoleType.STAFF, Set.of("STAFF"),
                Set.of("users.read")
        ), "ROLE_STAFF", "users.read");

        Page<UserResponse> page = userService.list(PageRequest.of(0, 50));

        Set<String> usernames = page.getContent().stream()
                .map(UserResponse::username)
                .collect(Collectors.toSet());

        assertTrue(usernames.contains("staff-a"),
                "Staff should see themselves because they are in org A");
        assertTrue(usernames.contains("member-a"),
                "Staff should see other org-A users");
        assertFalse(usernames.contains("member-b"),
                "Staff scoped to org A must not see org-B users");
        assertFalse(usernames.contains("root-admin"),
                "Staff should not see the cross-org admin (no organization_id)");
    }

    private Role saveRole(String code, String displayName) {
        Role r = new Role();
        r.setCode(code);
        r.setDisplayName(displayName);
        return roleRepository.save(r);
    }

    private User newUser(String username, String fullName, UserRoleType primary,
                         Long orgId, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        u.setPrimaryRole(primary);
        u.setEnabled(true);
        u.setOrganizationId(orgId);
        u.setRoles(new HashSet<>(Set.of(role)));
        return u;
    }

    private static void authenticateAs(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
