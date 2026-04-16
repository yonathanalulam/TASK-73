package com.dojostay.training;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/training — covers classes and sessions CRUD,
 * including session filtering and cancellation.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class TrainingControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private TrainingClassRepository classRepository;
    @Autowired private TrainingSessionRepository sessionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;
    private Long classId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        classRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("TRAIN-ORG");
        org.setName("Training Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("train-admin");
        admin.setFullName("Training Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        TrainingClass tc = new TrainingClass();
        tc.setOrganizationId(orgId);
        tc.setCode("BJJ-101");
        tc.setName("BJJ Basics");
        tc.setDiscipline("BJJ");
        tc.setLevel("BEGINNER");
        tc.setDefaultCapacity(20);
        classRepository.save(tc);
        classId = tc.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_classes_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/training/classes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_classes_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/training/classes").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_classes_as_admin_returns_classes() throws Exception {
        mockMvc.perform(get("/api/training/classes").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("BJJ-101"))
                .andExpect(jsonPath("$.data[0].discipline").value("BJJ"));
    }

    @Test
    void create_class_as_admin_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("code", "MUAY-201");
        body.put("name", "Muay Thai Intermediate");
        body.put("discipline", "MUAY_THAI");
        body.put("level", "INTERMEDIATE");
        body.put("defaultCapacity", 15);

        mockMvc.perform(post("/api/training/classes")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("MUAY-201"))
                .andExpect(jsonPath("$.data.discipline").value("MUAY_THAI"));
    }

    @Test
    void create_class_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("code", "FAIL");
        body.put("name", "Fail");
        body.put("discipline", "FAIL");
        body.put("level", "BEGINNER");
        body.put("defaultCapacity", 10);

        mockMvc.perform(post("/api/training/classes")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_sessions_for_class_returns_sessions() throws Exception {
        mockMvc.perform(get("/api/training/classes/" + classId + "/sessions")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void create_session_and_cancel_lifecycle() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("trainingClassId", classId);
        body.put("startsAt", "2026-08-01T10:00:00Z");
        body.put("endsAt", "2026-08-01T11:00:00Z");
        body.put("location", "Main Mat");
        body.put("capacity", 20);

        String resp = mockMvc.perform(post("/api/training/sessions")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.location").value("Main Mat"))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();

        Long sessionId = objectMapper.readTree(resp).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/training/sessions/" + sessionId)
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void filter_sessions_returns_results() throws Exception {
        mockMvc.perform(get("/api/training/sessions").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "train-admin", "Training Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("training.read", "training.write")),
                "ROLE_ADMIN", "training.read", "training.write"
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
