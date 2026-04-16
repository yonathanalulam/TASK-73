package com.dojostay.community;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * HTTP API test for /api/notifications — covers list, unread count,
 * and mark-as-read operations.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class NotificationControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long notifId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        notificationRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("notif-admin");
        admin.setFullName("Notification Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Notification n = new Notification();
        n.setRecipientUserId(adminId);
        n.setKind("MENTION");
        n.setTitle("You were mentioned");
        n.setBody("Someone mentioned you in a post.");
        n.setReferenceType("POST");
        n.setReferenceId("42");
        notificationRepository.save(n);
        notifId = n.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_notifications_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_notifications_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/notifications").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_notifications_as_admin_returns_list() throws Exception {
        mockMvc.perform(get("/api/notifications").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].kind").value("MENTION"))
                .andExpect(jsonPath("$.data[0].title").value("You were mentioned"));
    }

    @Test
    void unread_count_returns_count() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    void unread_count_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mark_read_succeeds() throws Exception {
        mockMvc.perform(post("/api/notifications/" + notifId + "/read")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        // Unread count should now be 0
        mockMvc.perform(get("/api/notifications/unread-count")
                        .with(authentication(adminAuth())))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void mark_read_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/api/notifications/" + notifId + "/read").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "notif-admin", "Notification Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("notifications.read")),
                "ROLE_ADMIN", "notifications.read"
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
