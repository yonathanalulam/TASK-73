package com.dojostay.roles;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/roles and /api/permissions.
 * Verifies read-only catalog endpoints are gated by users.read permission.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class RoleControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private Long adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        userRepository.deleteAll();
        roleRepository.deleteAll();

        Permission p = new Permission();
        p.setCode("users.read");
        p.setDisplayName("Read Users");
        permissionRepository.save(p);

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        adminRole.setPermissions(new HashSet<>(Set.of(p)));
        roleRepository.save(adminRole);

        Role staffRole = new Role();
        staffRole.setCode("STAFF");
        staffRole.setDisplayName("Staff");
        roleRepository.save(staffRole);

        User admin = new User();
        admin.setUsername("role-admin");
        admin.setFullName("Role Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_roles_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_roles_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/roles").with(authentication(noPerms)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_roles_as_admin_returns_roles_with_permissions() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/roles").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void list_permissions_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_permissions_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/permissions").with(authentication(noPerms)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_permissions_as_admin_returns_permission_catalog() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/permissions").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("users.read"));
    }

    private Authentication adminAuthentication() {
        return authenticationFor(
                new CurrentUser(adminId, "role-admin", "Role Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("users.read")),
                "ROLE_ADMIN", "users.read"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
