package com.dojostay.students;

import com.dojostay.DojoStayApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 smoke test — verifies:
 *   - student list/create are gated by the students.read/write permission,
 *   - admins can create and list students across organizations,
 *   - CSV bulk import produces a summary envelope and commits rows.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class StudentControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private BulkImportJobRepository jobRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private static final Long ORG_A = 100L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        jobRepository.deleteAll();
        studentRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Admin");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("root-admin");
        admin.setFullName("Root Admin");
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
    void list_without_authentication_returns_401() throws Exception {
        mockMvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_as_student_without_permission_returns_403() throws Exception {
        Authentication studentAuth = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/students").with(authentication(studentAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_create_and_list_students() throws Exception {
        Authentication adminAuth = adminAuthentication();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", ORG_A);
        body.put("fullName", "Akira Newcomer");
        body.put("email", "akira@example.test");
        body.put("externalId", "EXT-001");

        mockMvc.perform(post("/api/students")
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Akira Newcomer"))
                .andExpect(jsonPath("$.data.enrollmentStatus").value("PROSPECT"));

        mockMvc.perform(get("/api/students").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void csv_bulk_import_creates_rows_and_reports_skips() throws Exception {
        Authentication adminAuth = adminAuthentication();

        String csv = """
                externalId,fullName,email,phone,skillLevel
                EXT-100,Alice Beginner,alice@example.test,555-0100,white
                EXT-101,Bob Intermediate,bob@example.test,,yellow
                EXT-100,Alice Duplicate,alice2@example.test,,white
                ,,,,
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/students/import")
                        .file(file)
                        .param("organizationId", ORG_A.toString())
                        .with(authentication(adminAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdRows").value(2))
                .andExpect(jsonPath("$.data.skippedRows").value(1))
                .andExpect(jsonPath("$.data.failedRows").value(1));

        // Two real rows landed in the students table.
        long persisted = studentRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(2, persisted);
    }

    @Test
    void get_student_by_id_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/students/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_student_by_id_as_admin_returns_student() throws Exception {
        Authentication adminAuth = adminAuthentication();

        // Create a student first
        ObjectNode createBody = objectMapper.createObjectNode();
        createBody.put("organizationId", ORG_A);
        createBody.put("fullName", "Test Student");
        createBody.put("email", "test@example.test");
        createBody.put("externalId", "EXT-GET");

        String createResponse = mockMvc.perform(post("/api/students")
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType("application/json")
                        .content(createBody.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long studentId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/students/" + studentId).with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Test Student"))
                .andExpect(jsonPath("$.data.externalId").value("EXT-GET"));
    }

    @Test
    void update_student_as_admin_succeeds() throws Exception {
        Authentication adminAuth = adminAuthentication();

        // Create a student first
        ObjectNode createBody = objectMapper.createObjectNode();
        createBody.put("organizationId", ORG_A);
        createBody.put("fullName", "Before Update");
        createBody.put("email", "before@example.test");
        createBody.put("externalId", "EXT-UPD");

        String createResponse = mockMvc.perform(post("/api/students")
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType("application/json")
                        .content(createBody.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long studentId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        ObjectNode updateBody = objectMapper.createObjectNode();
        updateBody.put("fullName", "After Update");
        updateBody.put("skillLevel", "blue");

        mockMvc.perform(put("/api/students/" + studentId)
                        .with(authentication(adminAuth)).with(csrf())
                        .contentType("application/json")
                        .content(updateBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("After Update"))
                .andExpect(jsonPath("$.data.skillLevel").value("blue"));
    }

    @Test
    void student_me_without_self_read_returns_403() throws Exception {
        Authentication noSelfRead = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/students/me").with(authentication(noSelfRead)))
                .andExpect(status().isForbidden());
    }

    // ---- PUT /api/students/me tests ----

    @Test
    void update_my_profile_without_auth_returns_401() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("phone", "555-9999");

        mockMvc.perform(put("/api/students/me")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_my_profile_without_self_write_returns_403() throws Exception {
        Authentication noSelfWrite = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );

        ObjectNode body = objectMapper.createObjectNode();
        body.put("phone", "555-9999");

        mockMvc.perform(put("/api/students/me")
                        .with(authentication(noSelfWrite))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_my_profile_with_valid_student_succeeds() throws Exception {
        // Create a STUDENT role
        Role studentRole = new Role();
        studentRole.setCode("STUDENT");
        studentRole.setDisplayName("Student");
        roleRepository.save(studentRole);

        // Create a user with STUDENT role
        User studentUser = new User();
        studentUser.setUsername("self-student");
        studentUser.setFullName("Self Student");
        studentUser.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        studentUser.setPrimaryRole(UserRoleType.STUDENT);
        studentUser.setEnabled(true);
        studentUser.setRoles(new HashSet<>(Set.of(studentRole)));
        userRepository.save(studentUser);
        Long studentUserId = studentUser.getId();

        // Create a Student entity linked to that user
        Student student = new Student();
        student.setUserId(studentUserId);
        student.setOrganizationId(ORG_A);
        student.setFullName("Self Student");
        student.setEmail("self@example.test");
        student.setExternalId("EXT-SELF");
        studentRepository.save(student);

        // Authenticate as the student with self-write permission
        Authentication selfAuth = authenticationFor(
                new CurrentUser(studentUserId, "self-student", "Self Student",
                        UserRoleType.STUDENT, Set.of("STUDENT"),
                        Set.of("students.self.read", "students.self.write")),
                "ROLE_STUDENT", "students.self.read", "students.self.write"
        );

        ObjectNode body = objectMapper.createObjectNode();
        body.put("phone", "555-1234");
        body.put("emergencyContactName", "Parent Contact");
        body.put("emergencyContactPhone", "555-5678");
        body.put("notes", "Updated via self-service");

        mockMvc.perform(put("/api/students/me")
                        .with(authentication(selfAuth))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phone").value("555-1234"))
                .andExpect(jsonPath("$.data.emergencyContactName").value("Parent Contact"))
                .andExpect(jsonPath("$.data.emergencyContactPhone").value("555-5678"))
                .andExpect(jsonPath("$.data.notes").value("Updated via self-service"))
                .andExpect(jsonPath("$.data.fullName").value("Self Student"));
    }

    @Test
    void import_template_without_permission_returns_403() throws Exception {
        Authentication noImport = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/students/import/template").with(authentication(noImport)))
                .andExpect(status().isForbidden());
    }

    @Test
    void import_template_as_admin_returns_csv() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/students/import/template").with(authentication(adminAuth)))
                .andExpect(status().isOk());
    }

    @Test
    void import_errors_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/students/import/999/errors"))
                .andExpect(status().isUnauthorized());
    }

    private Authentication adminAuthentication() {
        return authenticationFor(
                new CurrentUser(adminId, "root-admin", "Root Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"),
                        Set.of("students.read", "students.write", "students.import")),
                "ROLE_ADMIN", "students.read", "students.write", "students.import"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
