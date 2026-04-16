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

import java.time.Instant;
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
 * HTTP API test for /api/bookings — covers booking lifecycle
 * (create, confirm, cancel, refund) and list operations.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class BookingControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private TrainingClassRepository classRepository;
    @Autowired private TrainingSessionRepository sessionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CreditTransactionRepository creditRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;
    private Long sessionId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        creditRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        classRepository.deleteAll();
        studentRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("BOOK-ORG");
        org.setName("Booking Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("book-admin");
        admin.setFullName("Booking Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Student student = new Student();
        student.setOrganizationId(orgId);
        student.setFullName("Booking Student");
        student.setExternalId("BOOK-EXT-1");
        studentRepository.save(student);
        studentId = student.getId();

        TrainingClass tc = new TrainingClass();
        tc.setOrganizationId(orgId);
        tc.setCode("BOOK-CLS");
        tc.setName("Booking Class");
        tc.setDiscipline("BJJ");
        tc.setLevel("BEGINNER");
        tc.setDefaultCapacity(10);
        classRepository.save(tc);

        TrainingSession ts = new TrainingSession();
        ts.setTrainingClassId(tc.getId());
        ts.setOrganizationId(orgId);
        ts.setStartsAt(Instant.parse("2026-08-10T10:00:00Z"));
        ts.setEndsAt(Instant.parse("2026-08-10T11:00:00Z"));
        ts.setLocation("Main Mat");
        ts.setCapacity(10);
        ts.setStatus(TrainingSession.Status.SCHEDULED);
        sessionRepository.save(ts);
        sessionId = ts.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_bookings_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_bookings_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/bookings").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void my_bookings_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/bookings/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void my_bookings_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/bookings/mine").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void list_bookings_by_session_returns_list() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .param("sessionId", sessionId.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void create_booking_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("trainingSessionId", sessionId);
        body.put("studentId", studentId);

        mockMvc.perform(post("/api/bookings")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trainingSessionId").value(sessionId))
                .andExpect(jsonPath("$.data.studentId").value(studentId))
                .andExpect(jsonPath("$.data.status").value("INITIATED"));
    }

    @Test
    void create_booking_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("trainingSessionId", sessionId);
        body.put("studentId", studentId);

        mockMvc.perform(post("/api/bookings")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirm_booking_succeeds() throws Exception {
        Long bookingId = createBooking();

        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void cancel_booking_succeeds() throws Exception {
        Long bookingId = createBooking();

        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void refund_booking_succeeds() throws Exception {
        Long bookingId = createBooking();

        // Confirm first
        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("creditAmount", 5);
        body.put("notes", "Refund test");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/refund")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    private Long createBooking() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("trainingSessionId", sessionId);
        body.put("studentId", studentId);

        String resp = mockMvc.perform(post("/api/bookings")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "book-admin", "Booking Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("bookings.read", "bookings.write",
                                "credits.read", "credits.write")),
                "ROLE_ADMIN", "bookings.read", "bookings.write",
                "credits.read", "credits.write"
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
