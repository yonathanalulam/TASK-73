package com.dojostay.e2e;

import com.dojostay.DojoStayApplication;
import com.dojostay.audit.AuditLogRepository;
import com.dojostay.auth.CurrentUser;
import com.dojostay.community.NotificationRepository;
import com.dojostay.community.PostCommentRepository;
import com.dojostay.community.PostLikeRepository;
import com.dojostay.community.PostMentionRepository;
import com.dojostay.community.PostRepository;
import com.dojostay.community.ThreadMuteRepository;
import com.dojostay.community.UserBlockRepository;
import com.dojostay.community.UserFollowRepository;
import com.dojostay.community.ModerationReportRepository;
import com.dojostay.organizations.Organization;
import com.dojostay.organizations.OrganizationRepository;
import com.dojostay.property.Bed;
import com.dojostay.property.BedRepository;
import com.dojostay.property.LodgingReservationRepository;
import com.dojostay.property.NightlyRateRepository;
import com.dojostay.property.Property;
import com.dojostay.property.PropertyAmenityRepository;
import com.dojostay.property.PropertyImageRepository;
import com.dojostay.property.PropertyRepository;
import com.dojostay.property.Room;
import com.dojostay.property.RoomRepository;
import com.dojostay.property.RoomTypeRepository;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.training.BookingRepository;
import com.dojostay.training.CreditTransactionRepository;
import com.dojostay.training.TrainingClass;
import com.dojostay.training.TrainingClassRepository;
import com.dojostay.training.TrainingSession;
import com.dojostay.training.TrainingSessionRepository;
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
 * Fullstack E2E integration test exercising a realistic FE-to-BE user journey
 * through the HTTP API layer.
 *
 * <p>This test simulates the exact HTTP calls that the Vue 3 SPA frontend makes
 * during a typical user session: login, browse training, book a session, post in
 * the community, and check notifications. Every call goes through MockMvc with
 * Spring Security applied, verifying the full request/response contract.
 *
 * <p><strong>Limitation:</strong> This test uses MockMvc (server-side) rather than
 * a real browser (Playwright/Selenium). The project does not include browser testing
 * infrastructure. This is the strongest feasible integration test that proves
 * real frontend API paths work end-to-end through the backend HTTP layer.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class FullStackE2EBrowserIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private PropertyAmenityRepository amenityRepository;
    @Autowired private PropertyImageRepository imageRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private NightlyRateRepository nightlyRateRepository;
    @Autowired private LodgingReservationRepository reservationRepository;
    @Autowired private TrainingClassRepository classRepository;
    @Autowired private TrainingSessionRepository sessionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CreditTransactionRepository creditRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCommentRepository commentRepository;
    @Autowired private PostLikeRepository likeRepository;
    @Autowired private PostMentionRepository mentionRepository;
    @Autowired private UserFollowRepository followRepository;
    @Autowired private UserBlockRepository blockRepository;
    @Autowired private ThreadMuteRepository muteRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ModerationReportRepository reportRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;
    private Long studentId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // Cleanup in dependency order
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        muteRepository.deleteAll();
        blockRepository.deleteAll();
        followRepository.deleteAll();
        mentionRepository.deleteAll();
        likeRepository.deleteAll();
        reportRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        creditRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        classRepository.deleteAll();
        nightlyRateRepository.deleteAll();
        roomTypeRepository.deleteAll();
        imageRepository.deleteAll();
        amenityRepository.deleteAll();
        reservationRepository.deleteAll();
        bedRepository.deleteAll();
        roomRepository.deleteAll();
        propertyRepository.deleteAll();
        studentRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        // Seed org, user, student, property, training data
        Organization org = new Organization();
        org.setCode("E2E-ORG");
        org.setName("E2E Test Organization");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("e2e-user");
        admin.setFullName("E2E User");
        admin.setPasswordHash(passwordEncoder.encode("E2ePass!2026xx"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Student student = new Student();
        student.setOrganizationId(orgId);
        student.setFullName("E2E Student");
        student.setExternalId("E2E-EXT-1");
        studentRepository.save(student);
        studentId = student.getId();

        Property prop = new Property();
        prop.setOrganizationId(orgId);
        prop.setCode("E2E-PROP");
        prop.setName("E2E Property");
        prop.setAddress("1 E2E Street");
        propertyRepository.save(prop);

        Room room = new Room();
        room.setPropertyId(prop.getId());
        room.setCode("E2E-R1");
        room.setName("E2E Room");
        room.setCapacity(2);
        roomRepository.save(room);

        Bed bed = new Bed();
        bed.setRoomId(room.getId());
        bed.setCode("E2E-B1");
        bed.setLabel("E2E Bed");
        bedRepository.save(bed);

        TrainingClass tc = new TrainingClass();
        tc.setOrganizationId(orgId);
        tc.setCode("E2E-BJJ");
        tc.setName("E2E BJJ Class");
        tc.setDiscipline("BJJ");
        tc.setLevel("ALL");
        tc.setDefaultCapacity(20);
        classRepository.save(tc);

        TrainingSession ts = new TrainingSession();
        ts.setTrainingClassId(tc.getId());
        ts.setOrganizationId(orgId);
        ts.setStartsAt(Instant.parse("2026-09-01T10:00:00Z"));
        ts.setEndsAt(Instant.parse("2026-09-01T11:00:00Z"));
        ts.setLocation("Main Dojo");
        ts.setCapacity(20);
        ts.setStatus(TrainingSession.Status.SCHEDULED);
        sessionRepository.save(ts);
        sessionId = ts.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Simulates a realistic frontend user journey:
     * 1. Login via POST /api/auth/login
     * 2. Fetch current user via GET /api/auth/me
     * 3. Browse training sessions via GET /api/training/sessions
     * 4. Book a session via POST /api/bookings
     * 5. Create a community post via POST /api/community/posts
     * 6. Add a comment via POST /api/community/comments
     * 7. Check notifications via GET /api/notifications
     * 8. Logout via POST /api/auth/logout
     *
     * Each step verifies the full request/response contract including JSON payload.
     */
    @Test
    void full_user_journey_login_browse_book_post_logout() throws Exception {
        // Step 1: Login
        ObjectNode loginBody = objectMapper.createObjectNode();
        loginBody.put("username", "e2e-user");
        loginBody.put("password", "E2ePass!2026xx");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(loginBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("e2e-user"))
                .andExpect(jsonPath("$.data.primaryRole").value("ADMIN"));

        Authentication auth = fullAdminAuth();

        // Step 2: Fetch current user profile (GET /api/auth/me)
        mockMvc.perform(get("/api/auth/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("e2e-user"));

        // Step 3: Browse training sessions
        mockMvc.perform(get("/api/training/sessions").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].location").value("Main Dojo"));

        // Step 4: Book a session
        ObjectNode bookBody = objectMapper.createObjectNode();
        bookBody.put("trainingSessionId", sessionId);
        bookBody.put("studentId", studentId);

        String bookResp = mockMvc.perform(post("/api/bookings")
                        .with(authentication(auth)).with(csrf())
                        .contentType("application/json")
                        .content(bookBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("INITIATED"))
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(bookResp).path("data").path("id").asLong();

        // Step 5: Create a community post
        ObjectNode postBody = objectMapper.createObjectNode();
        postBody.put("organizationId", orgId);
        postBody.put("title", "Just signed up for BJJ!");
        postBody.put("body", "Looking forward to training at the Main Dojo tomorrow.");
        postBody.put("visibility", "ORGANIZATION");

        String postResp = mockMvc.perform(post("/api/community/posts")
                        .with(authentication(auth)).with(csrf())
                        .contentType("application/json")
                        .content(postBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Just signed up for BJJ!"))
                .andReturn().getResponse().getContentAsString();

        Long postId = objectMapper.readTree(postResp).path("data").path("id").asLong();

        // Step 6: Add a comment
        ObjectNode commentBody = objectMapper.createObjectNode();
        commentBody.put("postId", postId);
        commentBody.put("body", "Can't wait to start training!");

        mockMvc.perform(post("/api/community/comments")
                        .with(authentication(auth)).with(csrf())
                        .contentType("application/json")
                        .content(commentBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.body").value("Can't wait to start training!"));

        // Step 7: Check notifications
        mockMvc.perform(get("/api/notifications").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Step 8: Logout
        mockMvc.perform(post("/api/auth/logout")
                        .with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Verifies that unauthenticated access to protected endpoints is blocked,
     * simulating a frontend that hasn't logged in yet.
     */
    @Test
    void unauthenticated_journey_is_blocked_at_every_protected_endpoint() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/training/sessions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/community/posts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    private Authentication fullAdminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "e2e-user", "E2E User", UserRoleType.ADMIN,
                        Set.of("ADMIN"),
                        Set.of("users.read", "users.write", "students.read", "students.write",
                                "training.read", "training.write", "bookings.read", "bookings.write",
                                "property.read", "property.write", "community.read", "community.write",
                                "notifications.read", "moderation.review", "credentials.review",
                                "risk.read", "risk.write", "scopes.read", "scopes.write",
                                "ops.toggles.read", "ops.toggles.write", "ops.backups.read",
                                "exports.read", "credits.read", "credits.write",
                                "orgs.read", "orgs.write", "reservations.read", "reservations.write",
                                "students.import", "students.self.read", "students.self.write")),
                "ROLE_ADMIN", "users.read", "users.write", "students.read", "students.write",
                "training.read", "training.write", "bookings.read", "bookings.write",
                "property.read", "property.write", "community.read", "community.write",
                "notifications.read", "moderation.review", "credentials.review",
                "risk.read", "risk.write", "scopes.read", "scopes.write",
                "ops.toggles.read", "ops.toggles.write", "ops.backups.read",
                "exports.read", "credits.read", "credits.write",
                "orgs.read", "orgs.write", "reservations.read", "reservations.write",
                "students.import", "students.self.read", "students.self.write"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
