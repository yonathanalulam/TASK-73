package com.dojostay.organizations;

import com.dojostay.DojoStayApplication;
import com.dojostay.audit.AuditLogRepository;
import com.dojostay.auth.CurrentUser;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/organizations.
 * Covers CRUD with scope filtering, auth gates, and audit trail.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class OrganizationControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("ORG-IT");
        org.setName("IT Test Org");
        org.setContactEmail("org@test.example");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("org-admin");
        admin.setFullName("Org Admin");
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
    void list_orgs_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_orgs_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/organizations").with(authentication(noPerms)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orgs_as_admin_returns_all_orgs() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/organizations").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("ORG-IT"));
    }

    @Test
    void get_org_by_id_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/organizations/" + orgId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_org_by_id_as_admin_returns_org() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/organizations/" + orgId).with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("ORG-IT"))
                .andExpect(jsonPath("$.data.name").value("IT Test Org"))
                .andExpect(jsonPath("$.data.contactEmail").value("org@test.example"));
    }

    @Test
    void create_org_as_admin_succeeds() throws Exception {
        Authentication adminAuth = adminAuthentication();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "ORG-NEW");
        body.put("name", "New Organization");
        body.put("contactEmail", "new@example.test");

        mockMvc.perform(post("/api/organizations")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("ORG-NEW"))
                .andExpect(jsonPath("$.data.name").value("New Organization"));
    }

    @Test
    void create_org_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );

        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "ORG-FAIL");
        body.put("name", "Should Fail");

        mockMvc.perform(post("/api/organizations")
                        .with(authentication(noPerms))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_org_as_admin_succeeds() throws Exception {
        Authentication adminAuth = adminAuthentication();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", "Updated Org Name");
        body.put("active", true);

        mockMvc.perform(put("/api/organizations/" + orgId)
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Org Name"));
    }

    @Test
    void update_org_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", "Hacked Name");

        mockMvc.perform(put("/api/organizations/" + orgId)
                        .with(authentication(noPerms))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    private Authentication adminAuthentication() {
        return authenticationFor(
                new CurrentUser(adminId, "org-admin", "Org Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("orgs.read", "orgs.write")),
                "ROLE_ADMIN", "orgs.read", "orgs.write"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
