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
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
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
 * HTTP API test for /api/credits — covers credit balance,
 * history, and adjustment operations.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class CreditControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CreditTransactionRepository creditRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        creditRepository.deleteAll();
        studentRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("CRED-ORG");
        org.setName("Credit Test Org");
        organizationRepository.save(org);

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("cred-admin");
        admin.setFullName("Credit Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Student student = new Student();
        student.setOrganizationId(org.getId());
        student.setFullName("Credit Student");
        student.setExternalId("CRED-EXT-1");
        studentRepository.save(student);
        studentId = student.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void balance_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/credits/students/" + studentId + "/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void balance_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/credits/students/" + studentId + "/balance")
                        .with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void balance_as_admin_returns_zero_initially() throws Exception {
        mockMvc.perform(get("/api/credits/students/" + studentId + "/balance")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentId").value(studentId))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    void history_returns_empty_initially() throws Exception {
        mockMvc.perform(get("/api/credits/students/" + studentId + "/history")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void adjust_credits_as_admin_creates_transaction() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("studentId", studentId);
        body.put("delta", 10);
        body.put("reason", "MANUAL_ADJUSTMENT");
        body.put("notes", "Test credit adjustment");

        mockMvc.perform(post("/api/credits/adjust")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.delta").value(10))
                .andExpect(jsonPath("$.data.balanceAfter").value(10))
                .andExpect(jsonPath("$.data.reason").value("MANUAL_ADJUSTMENT"));

        // Verify balance updated
        mockMvc.perform(get("/api/credits/students/" + studentId + "/balance")
                        .with(authentication(adminAuth())))
                .andExpect(jsonPath("$.data.balance").value(10));
    }

    @Test
    void adjust_credits_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("studentId", studentId);
        body.put("delta", 10);
        body.put("reason", "MANUAL_ADJUSTMENT");

        mockMvc.perform(post("/api/credits/adjust")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "cred-admin", "Credit Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("credits.read", "credits.write")),
                "ROLE_ADMIN", "credits.read", "credits.write"
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
