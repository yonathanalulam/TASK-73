package com.dojostay.users;

import com.dojostay.DojoStayApplication;
import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditLog;
import com.dojostay.audit.AuditLogRepository;
import com.dojostay.auth.CurrentUser;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRuleRepository;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API test for /api/users. Covers the Phase 2 authorization contract:
 *
 * <ul>
 *   <li>Unauthenticated callers get 401.</li>
 *   <li>Authenticated callers without {@code users.read} get 403.</li>
 *   <li>Admins can list users and assign roles; role changes produce audit rows.</li>
 * </ul>
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class UserControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = saveRole("ADMIN", "Administrator");
        Role staffRole = saveRole("STAFF", "Staff");
        saveRole("STUDENT", "Student");

        User admin = new User();
        admin.setUsername("root-admin");
        admin.setFullName("Root Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        User target = new User();
        target.setUsername("jane-student");
        target.setFullName("Jane Student");
        target.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        target.setPrimaryRole(UserRoleType.STUDENT);
        target.setEnabled(true);
        target.setRoles(new HashSet<>());
        userRepository.save(target);
        targetId = target.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_without_authentication_returns_401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_as_student_without_users_read_returns_403() throws Exception {
        Authentication studentAuth = authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );

        mockMvc.perform(get("/api/users").with(authentication(studentAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_as_admin_returns_all_users_in_envelope() throws Exception {
        Authentication adminAuth = adminAuthentication();

        mockMvc.perform(get("/api/users").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void assign_roles_as_admin_updates_user_and_writes_audit_rows() throws Exception {
        Authentication adminAuth = adminAuthentication();

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode codes = body.putArray("roleCodes");
        codes.add("STAFF");

        mockMvc.perform(put("/api/users/" + targetId + "/roles")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItem("STAFF")));

        User updated = userRepository.findById(targetId).orElseThrow();
        Set<String> updatedCodes = updated.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());
        assertEquals(Set.of("STAFF"), updatedCodes);

        List<AuditLog> audits = auditLogRepository.findAll();
        assertTrue(audits.stream()
                        .anyMatch(a -> AuditAction.ROLE_ASSIGNED.name().equals(a.getAction())
                                && String.valueOf(targetId).equals(a.getTargetId())),
                "Expected a ROLE_ASSIGNED audit row for the target user");
    }

    private Role saveRole(String code, String displayName) {
        Role r = new Role();
        r.setCode(code);
        r.setDisplayName(displayName);
        return roleRepository.save(r);
    }

    private Authentication adminAuthentication() {
        return authenticationFor(
                new CurrentUser(adminId, "root-admin", "Root Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("users.read", "users.write")),
                "ROLE_ADMIN", "users.read", "users.write"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
