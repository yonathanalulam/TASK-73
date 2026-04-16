package com.dojostay.security;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for security filters and middleware behavior:
 * - CorrelationIdFilter: X-Correlation-Id propagation
 * - RateLimitFilter: rate limiting behavior
 * - SecurityConfig: auth-required and role-gated routes
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class SecurityFilterIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("sec-admin");
        admin.setFullName("Security Admin");
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

    // ---- CorrelationIdFilter Tests ----

    @Test
    void correlation_id_is_generated_when_not_provided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertNotNull(correlationId, "X-Correlation-Id response header should be present");
        assertTrue(correlationId.length() >= 16, "Generated correlation ID should be at least 16 chars");
    }

    @Test
    void correlation_id_is_echoed_when_provided() throws Exception {
        String customId = "test-trace-12345";

        mockMvc.perform(get("/api/auth/csrf")
                        .header("X-Correlation-Id", customId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", customId));
    }

    // ---- Auth-Required Gate Tests ----

    @Test
    void public_endpoints_accessible_without_auth() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void login_endpoint_accessible_without_auth() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", "sec-admin");
        body.put("password", "Seeded-Pass!9");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void protected_endpoint_returns_401_without_auth() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protected_endpoint_returns_403_without_permission() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );

        mockMvc.perform(get("/api/users").with(authentication(noPerms)))
                .andExpect(status().isForbidden());
    }

    // ---- Rate Limit Filter Tests ----

    @Test
    void rate_limit_filter_class_exists_on_classpath() throws Exception {
        Class<?> clazz = Class.forName("com.dojostay.common.RateLimitFilter");
        assertNotNull(clazz, "RateLimitFilter should be on classpath");
    }

    @Test
    void rate_limit_allows_normal_requests() throws Exception {
        // Several normal login attempts should not trigger rate limiting
        // (default is 30/min for auth bucket)
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", "sec-admin");
        body.put("password", "Seeded-Pass!9");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType("application/json")
                            .content(body.toString()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void authenticated_write_requests_are_not_rate_limited_under_normal_load() throws Exception {
        Authentication adminAuth = authenticationFor(
                new CurrentUser(adminId, "sec-admin", "Security Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("community.write", "community.read")),
                "ROLE_ADMIN", "community.write", "community.read"
        );

        // Several write requests should succeed under normal load
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/logout")
                            .with(authentication(adminAuth))
                            .with(csrf()))
                    .andExpect(status().isOk());
        }
    }

    // ---- CSRF Tests ----

    @Test
    void post_without_csrf_token_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", "sec-admin");
        body.put("password", "Seeded-Pass!9");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
