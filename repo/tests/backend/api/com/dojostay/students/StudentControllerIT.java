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
