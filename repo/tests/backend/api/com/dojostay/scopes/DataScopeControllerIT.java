package com.dojostay.scopes;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/scopes.
 * Verifies data scope CRUD and permission enforcement.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class DataScopeControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long targetUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        Role staffRole = new Role();
        staffRole.setCode("STAFF");
        staffRole.setDisplayName("Staff");
        roleRepository.save(staffRole);

        User admin = new User();
        admin.setUsername("scope-admin");
        admin.setFullName("Scope Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        User target = new User();
        target.setUsername("scope-target");
        target.setFullName("Scope Target");
        target.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        target.setPrimaryRole(UserRoleType.STAFF);
        target.setEnabled(true);
        target.setRoles(new HashSet<>(Set.of(staffRole)));
        userRepository.save(target);
        targetUserId = target.getId();

        DataScopeRule rule = new DataScopeRule();
        rule.setUserId(targetUserId);
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(100L);
        scopeRuleRepository.save(rule);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_all_scopes_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/scopes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_all_scopes_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
        mockMvc.perform(get("/api/scopes").with(authentication(noPerms)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_all_scopes_as_admin_returns_rules() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/scopes").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].scopeType").value("ORGANIZATION"));
    }

    @Test
    void list_scopes_for_user_returns_user_rules() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/scopes/users/" + targetUserId).with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userId").value(targetUserId));
    }

    @Test
    void replace_scopes_for_user_as_admin_succeeds() throws Exception {
        Authentication adminAuth = adminAuthentication();

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode rules = body.putArray("rules");
        ObjectNode r1 = rules.addObject();
        r1.put("scopeType", "ORGANIZATION");
        r1.put("scopeTargetId", 200);

        mockMvc.perform(put("/api/scopes/users/" + targetUserId)
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].scopeTargetId").value(200));

        List<DataScopeRule> persisted = scopeRuleRepository.findAll();
        assertEquals(1, persisted.size());
        assertEquals(200L, persisted.get(0).getScopeTargetId());
    }

    @Test
    void replace_scopes_without_permission_returns_403() throws Exception {
        Authentication noPerms = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );

        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("rules");

        mockMvc.perform(put("/api/scopes/users/" + targetUserId)
                        .with(authentication(noPerms))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    private Authentication adminAuthentication() {
        return authenticationFor(
                new CurrentUser(adminId, "scope-admin", "Scope Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("scopes.read", "scopes.write")),
                "ROLE_ADMIN", "scopes.read", "scopes.write"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
