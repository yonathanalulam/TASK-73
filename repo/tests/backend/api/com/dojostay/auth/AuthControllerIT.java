package com.dojostay.auth;

import com.dojostay.DojoStayApplication;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.users.User;
import com.dojostay.users.UserLockState;
import com.dojostay.users.UserLockStateRepository;
import com.dojostay.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test for the auth slice.
 *
 * <p>Boots the real Spring context against H2 (MODE=MySQL) so Flyway migrations and
 * Spring Security configuration are exercised end-to-end.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserLockStateRepository lockRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // Wipe and re-seed minimal data per test for isolation.
        lockRepo.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role admin = new Role();
        admin.setCode("ADMIN");
        admin.setDisplayName("Administrator");
        roleRepository.save(admin);

        User u = new User();
        u.setUsername("alice");
        u.setFullName("Alice Admin");
        u.setPasswordHash(passwordEncoder.encode("CorrectHorse9!"));
        u.setPrimaryRole(UserRoleType.ADMIN);
        u.setEnabled(true);
        u.setRoles(new HashSet<>(Set.of(admin)));
        userRepository.save(u);
    }

    @Test
    void me_without_session_returns_401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_with_valid_credentials_returns_user_envelope() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", "alice");
        body.put("password", "CorrectHorse9!");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.primaryRole").value("ADMIN"));
    }

    @Test
    void login_with_bad_password_returns_401_envelope() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", "alice");
        body.put("password", "WrongPassword!1");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void five_failed_logins_lock_the_account() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", "alice");
        body.put("password", "WrongPassword!1");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType("application/json")
                            .content(body.toString()))
                    .andExpect(status().isUnauthorized());
        }

        Long aliceId = userRepository.findByUsername("alice").orElseThrow().getId();
        UserLockState state = lockRepo.findById(aliceId).orElseThrow();
        assertNotNull(state.getLockedUntil(), "Account should have lockedUntil set");
        assertTrue(state.getFailedAttempts() >= 5);

        // Subsequent attempts (even with the right password) should be rejected with 423.
        ObjectNode rightBody = objectMapper.createObjectNode();
        rightBody.put("username", "alice");
        rightBody.put("password", "CorrectHorse9!");
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(rightBody.toString()))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }
}
