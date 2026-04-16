package com.dojostay.credentials;

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
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/credentials — covers credential review
 * submission (JSON and file upload), listing, decision, and blacklist.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class CredentialReviewControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CredentialReviewRepository reviewRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        reviewRepository.deleteAll();
        studentRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("CRED-ORG");
        org.setName("Credential Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("cred-admin");
        admin.setFullName("Credential Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Student student = new Student();
        student.setOrganizationId(orgId);
        student.setFullName("Credential Student");
        student.setExternalId("CRED-EXT-1");
        studentRepository.save(student);
        studentId = student.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_reviews_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/credentials/reviews"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_reviews_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/credentials/reviews").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_reviews_as_admin_returns_empty_initially() throws Exception {
        mockMvc.perform(get("/api/credentials/reviews").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void submit_review_json_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("studentId", studentId);
        body.put("discipline", "BJJ");
        body.put("requestedRank", "Blue Belt");
        body.put("currentRank", "White Belt");
        body.put("evidence", "3 years training, competition record");

        mockMvc.perform(post("/api/credentials/reviews")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discipline").value("BJJ"))
                .andExpect(jsonPath("$.data.requestedRank").value("Blue Belt"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void submit_review_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("studentId", studentId);
        body.put("discipline", "BJJ");
        body.put("requestedRank", "Blue Belt");

        mockMvc.perform(post("/api/credentials/reviews")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void submit_review_with_file_upload_succeeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "fake-pdf-content".getBytes());

        mockMvc.perform(multipart("/api/credentials/reviews/upload")
                        .file(file)
                        .param("studentId", studentId.toString())
                        .param("discipline", "Judo")
                        .param("requestedRank", "Black Belt")
                        .param("currentRank", "Brown Belt")
                        .param("evidence", "International competition medals")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discipline").value("Judo"))
                .andExpect(jsonPath("$.data.requestedRank").value("Black Belt"))
                .andExpect(jsonPath("$.data.fileName").value("cert.pdf"));
    }

    @Test
    void decide_review_approve_succeeds() throws Exception {
        Long reviewId = submitReview();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("decision", "APPROVED");
        body.put("reviewNotes", "Credentials verified against records.");

        mockMvc.perform(post("/api/credentials/reviews/" + reviewId + "/decide")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewNotes").value("Credentials verified against records."));
    }

    @Test
    void blacklist_user_succeeds() throws Exception {
        Long reviewId = submitReview();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("reason", "Fraudulent credential submission");

        mockMvc.perform(post("/api/credentials/reviews/" + reviewId + "/blacklist")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private Long submitReview() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("studentId", studentId);
        body.put("discipline", "BJJ");
        body.put("requestedRank", "Purple Belt");
        body.put("currentRank", "Blue Belt");
        body.put("evidence", "4 years competitive experience");

        String resp = mockMvc.perform(post("/api/credentials/reviews")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "cred-admin", "Credential Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("credentials.review")),
                "ROLE_ADMIN", "credentials.review"
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
