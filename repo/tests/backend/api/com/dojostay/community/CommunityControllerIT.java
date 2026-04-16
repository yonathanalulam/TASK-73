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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/community — covers posts, comments, likes,
 * follows, thread mute, and user block operations.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class CommunityControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCommentRepository commentRepository;
    @Autowired private PostLikeRepository likeRepository;
    @Autowired private UserFollowRepository followRepository;
    @Autowired private UserBlockRepository blockRepository;
    @Autowired private ThreadMuteRepository muteRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PostMentionRepository mentionRepository;
    @Autowired private ModerationReportRepository reportRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long secondUserId;
    private Long orgId;

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
        org.setCode("COMM-ORG");
        org.setName("Community Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("comm-admin");
        admin.setFullName("Community Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        User second = new User();
        second.setUsername("comm-user2");
        second.setFullName("Second User");
        second.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        second.setPrimaryRole(UserRoleType.STAFF);
        second.setEnabled(true);
        second.setRoles(new HashSet<>());
        userRepository.save(second);
        secondUserId = second.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- Posts ----

    @Test
    void list_posts_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/community/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_posts_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/community/posts").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_posts_as_admin_returns_empty_initially() throws Exception {
        mockMvc.perform(get("/api/community/posts").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void create_post_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("title", "Test Post");
        body.put("body", "This is a test post body.");
        body.put("visibility", "ORGANIZATION");

        mockMvc.perform(post("/api/community/posts")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Test Post"))
                .andExpect(jsonPath("$.data.body").value("This is a test post body."))
                .andExpect(jsonPath("$.data.visibility").value("ORGANIZATION"));
    }

    @Test
    void create_post_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("title", "Fail");
        body.put("body", "Should fail");
        body.put("visibility", "ORGANIZATION");

        mockMvc.perform(post("/api/community/posts")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    // ---- Comments ----

    @Test
    void create_comment_and_list_comments() throws Exception {
        Long postId = createPost();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("postId", postId);
        body.put("body", "First comment on the post.");

        mockMvc.perform(post("/api/community/comments")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.body").value("First comment on the post."))
                .andExpect(jsonPath("$.data.postId").value(postId));

        mockMvc.perform(get("/api/community/posts/" + postId + "/comments")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ---- Likes ----

    @Test
    void like_and_unlike_post() throws Exception {
        Long postId = createPost();

        mockMvc.perform(post("/api/community/posts/" + postId + "/like")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        mockMvc.perform(delete("/api/community/posts/" + postId + "/like")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    @Test
    void like_and_unlike_comment() throws Exception {
        Long postId = createPost();
        Long commentId = createComment(postId);

        mockMvc.perform(post("/api/community/comments/" + commentId + "/like")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        mockMvc.perform(delete("/api/community/comments/" + commentId + "/like")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    // ---- Follows ----

    @Test
    void follow_and_unfollow_user() throws Exception {
        mockMvc.perform(post("/api/community/users/" + secondUserId + "/follow")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.followedUserId").value(secondUserId));

        mockMvc.perform(get("/api/community/me/following")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/community/users/" + secondUserId + "/follow")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void list_following_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/community/me/following"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_followers_returns_list() throws Exception {
        mockMvc.perform(get("/api/community/me/followers")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---- Thread Mute ----

    @Test
    void mute_and_unmute_thread() throws Exception {
        Long postId = createPost();

        mockMvc.perform(post("/api/community/posts/" + postId + "/mute")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/community/posts/" + postId + "/mute")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- User Block ----

    @Test
    void block_and_unblock_user() throws Exception {
        mockMvc.perform(post("/api/community/users/" + secondUserId + "/block")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/community/users/" + secondUserId + "/block")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- Helpers ----

    private Long createPost() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("title", "Helper Post");
        body.put("body", "Helper post body.");
        body.put("visibility", "ORGANIZATION");

        String resp = mockMvc.perform(post("/api/community/posts")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Long createComment(Long postId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("postId", postId);
        body.put("body", "Helper comment.");

        String resp = mockMvc.perform(post("/api/community/comments")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "comm-admin", "Community Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("community.read", "community.write")),
                "ROLE_ADMIN", "community.read", "community.write"
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
