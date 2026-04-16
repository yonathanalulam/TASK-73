package com.dojostay.risk;

import com.dojostay.DojoStayApplication;
import com.dojostay.audit.AuditLogRepository;
import com.dojostay.auth.CurrentUser;
import com.dojostay.organizations.Organization;
import com.dojostay.organizations.OrganizationRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/risk — covers risk flags and incident logging.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class RiskControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RiskFlagRepository flagRepository;
    @Autowired private IncidentLogRepository incidentRepository;
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
        incidentRepository.deleteAll();
        flagRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("RISK-ORG");
        org.setName("Risk Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("risk-admin");
        admin.setFullName("Risk Admin");
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
    void list_flags_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/risk/flags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_flags_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/risk/flags").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_flags_as_admin_returns_empty_initially() throws Exception {
        mockMvc.perform(get("/api/risk/flags").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void raise_flag_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("subjectType", "STUDENT");
        body.put("subjectId", 1);
        body.put("category", "BEHAVIORAL");
        body.put("severity", "MEDIUM");
        body.put("description", "Repeated policy violations observed");

        mockMvc.perform(post("/api/risk/flags")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("BEHAVIORAL"))
                .andExpect(jsonPath("$.data.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void raise_flag_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("subjectType", "STUDENT");
        body.put("subjectId", 1);
        body.put("category", "FAIL");
        body.put("severity", "LOW");
        body.put("description", "Should fail");

        mockMvc.perform(post("/api/risk/flags")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clear_flag_succeeds() throws Exception {
        Long flagId = raiseFlag();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("clearanceNotes", "Issue resolved after counseling session.");

        mockMvc.perform(post("/api/risk/flags/" + flagId + "/clear")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CLEARED"))
                .andExpect(jsonPath("$.data.clearanceNotes").value("Issue resolved after counseling session."));
    }

    @Test
    void list_incidents_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/risk/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_incidents_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/risk/incidents").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void log_incident_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("occurredAt", "2026-07-15T14:30:00Z");
        body.put("subjectType", "PROPERTY");
        body.put("subjectId", 1);
        body.put("category", "SAFETY");
        body.put("severity", "HIGH");
        body.put("description", "Water leak detected in training room");
        body.put("followUp", "Maintenance team notified, area cordoned off");

        mockMvc.perform(post("/api/risk/incidents")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("SAFETY"))
                .andExpect(jsonPath("$.data.severity").value("HIGH"))
                .andExpect(jsonPath("$.data.description").value("Water leak detected in training room"));
    }

    @Test
    void log_incident_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("occurredAt", "2026-07-15T14:30:00Z");
        body.put("subjectType", "OTHER");
        body.put("subjectId", 1);
        body.put("category", "FAIL");
        body.put("severity", "LOW");
        body.put("description", "Should fail");

        mockMvc.perform(post("/api/risk/incidents")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    private Long raiseFlag() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("subjectType", "STUDENT");
        body.put("subjectId", 1);
        body.put("category", "BEHAVIORAL");
        body.put("severity", "LOW");
        body.put("description", "Minor concern");

        String resp = mockMvc.perform(post("/api/risk/flags")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "risk-admin", "Risk Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("risk.read", "risk.write")),
                "ROLE_ADMIN", "risk.read", "risk.write"
        );
    }

    private Authentication noPermsAuth() {
        return authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
