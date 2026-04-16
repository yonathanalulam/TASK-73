package com.dojostay.ops;

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
 * HTTP API test for /api/ops — covers feature toggles, backups,
 * exports, and ops job management.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class OpsControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private FeatureToggleRepository toggleRepository;
    @Autowired private BackupStatusRepository backupRepository;
    @Autowired private ExportJobRepository exportRepository;
    @Autowired private OpsJobRecordRepository jobRepository;
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
        jobRepository.deleteAll();
        exportRepository.deleteAll();
        backupRepository.deleteAll();
        toggleRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("OPS-ORG");
        org.setName("Ops Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("ops-admin");
        admin.setFullName("Ops Admin");
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

    // ---- Feature Toggles ----

    @Test
    void list_toggles_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/ops/toggles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_toggles_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/ops/toggles").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_toggles_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/ops/toggles").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upsert_toggle_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "DARK_MODE");
        body.put("description", "Enable dark mode UI");
        body.put("enabled", true);

        mockMvc.perform(post("/api/ops/toggles")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("DARK_MODE"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void upsert_toggle_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "FAIL_TOGGLE");
        body.put("enabled", true);

        mockMvc.perform(post("/api/ops/toggles")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    // ---- Backups ----

    @Test
    void list_backups_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/ops/backups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_backups_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/ops/backups").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void record_backup_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("kind", "FULL_DB");
        body.put("status", "SUCCEEDED");
        body.put("startedAt", "2026-07-15T02:00:00Z");
        body.put("completedAt", "2026-07-15T02:15:00Z");
        body.put("location", "/backups/2026-07-15-full.sql.gz");
        body.put("sizeBytes", 1048576);
        body.put("durationMs", 900000);
        body.put("notes", "Scheduled nightly full backup");

        mockMvc.perform(post("/api/ops/backups")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kind").value("FULL_DB"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    // ---- Exports ----

    @Test
    void list_exports_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/ops/exports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_exports_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/ops/exports").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void request_export_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("kind", "STUDENTS");
        body.put("format", "CSV");

        mockMvc.perform(post("/api/ops/exports")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kind").value("STUDENTS"))
                .andExpect(jsonPath("$.data.format").value("CSV"));
    }

    // ---- Ops Jobs ----

    @Test
    void list_ops_jobs_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/ops/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_ops_jobs_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/ops/jobs").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void list_ops_jobs_by_kind_returns_list() throws Exception {
        mockMvc.perform(get("/api/ops/jobs/BACKUP").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void trigger_ops_job_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jobKind", "ANOMALY_SCAN");

        mockMvc.perform(post("/api/ops/jobs/trigger")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobKind").value("ANOMALY_SCAN"));
    }

    @Test
    void trigger_ops_job_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jobKind", "ANOMALY_SCAN");

        mockMvc.perform(post("/api/ops/jobs/trigger")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "ops-admin", "Ops Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("ops.toggles.read", "ops.toggles.write",
                                "ops.backups.read", "exports.read")),
                "ROLE_ADMIN", "ops.toggles.read", "ops.toggles.write",
                "ops.backups.read", "exports.read"
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
