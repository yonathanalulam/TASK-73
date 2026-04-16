package com.dojostay.community;

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
 * HTTP API test for /api/moderation — covers report filing,
 * listing, resolution, and content restore.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class ModerationControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCommentRepository commentRepository;
    @Autowired private PostLikeRepository likeRepository;
    @Autowired private PostMentionRepository mentionRepository;
    @Autowired private ModerationReportRepository reportRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ThreadMuteRepository muteRepository;
    @Autowired private UserFollowRepository followRepository;
    @Autowired private UserBlockRepository blockRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;
    private Long postId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

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
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("MOD-ORG");
        org.setName("Moderation Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("mod-admin");
        admin.setFullName("Moderation Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Post p = new Post();
        p.setOrganizationId(orgId);
        p.setAuthorUserId(adminId);
        p.setTitle("Reportable Post");
        p.setBody("Content to be reported.");
        p.setVisibility(Post.Visibility.ORGANIZATION);
        p.setStatus(Post.Status.PUBLISHED);
        postRepository.save(p);
        postId = p.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_reports_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/moderation/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_reports_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/moderation/reports").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_reports_as_admin_returns_empty_initially() throws Exception {
        mockMvc.perform(get("/api/moderation/reports").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void file_report_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("targetType", "POST");
        body.put("targetId", postId);
        body.put("reason", "SPAM");
        body.put("details", "This post is spam content.");

        mockMvc.perform(post("/api/moderation/reports")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetType").value("POST"))
                .andExpect(jsonPath("$.data.targetId").value(postId))
                .andExpect(jsonPath("$.data.reason").value("SPAM"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void file_report_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("targetType", "POST");
        body.put("targetId", postId);
        body.put("reason", "SPAM");

        mockMvc.perform(post("/api/moderation/reports")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolve_report_succeeds() throws Exception {
        Long reportId = fileReport();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("resolution", "UPHELD");
        body.put("resolutionNotes", "Confirmed spam, removing content.");
        body.put("hideTarget", true);
        body.put("hiddenReason", "Spam confirmed by moderator");

        mockMvc.perform(post("/api/moderation/reports/" + reportId + "/resolve")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UPHELD"))
                .andExpect(jsonPath("$.data.resolutionNotes").value("Confirmed spam, removing content."));
    }

    @Test
    void restore_content_succeeds() throws Exception {
        Long reportId = fileReport();

        // Resolve with hide
        ObjectNode resolveBody = objectMapper.createObjectNode();
        resolveBody.put("resolution", "UPHELD");
        resolveBody.put("resolutionNotes", "Hide it");
        resolveBody.put("hideTarget", true);
        resolveBody.put("hiddenReason", "Test hide");

        mockMvc.perform(post("/api/moderation/reports/" + reportId + "/resolve")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(resolveBody.toString()))
                .andExpect(status().isOk());

        // Restore
        mockMvc.perform(post("/api/moderation/restore")
                        .param("targetType", "POST")
                        .param("targetId", postId.toString())
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private Long fileReport() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("targetType", "POST");
        body.put("targetId", postId);
        body.put("reason", "SPAM");
        body.put("details", "Spam report");

        String resp = mockMvc.perform(post("/api/moderation/reports")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "mod-admin", "Moderation Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("moderation.review", "community.read",
                                "community.write")),
                "ROLE_ADMIN", "moderation.review", "community.read", "community.write"
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
