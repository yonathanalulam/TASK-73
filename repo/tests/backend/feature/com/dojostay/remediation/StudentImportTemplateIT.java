package com.dojostay.remediation;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.roles.UserRoleType;
import com.dojostay.students.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D11 — B5 student import template test.
 * Verifies that the CSV template has the expected header columns.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class StudentImportTemplateIT {

    @Autowired private StudentService studentService;

    @BeforeEach
    void setUp() {
        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void import_template_contains_expected_columns() {
        String template = studentService.buildImportTemplate();

        assertTrue(template.contains("externalId"), "Template must have externalId column");
        assertTrue(template.contains("fullName"), "Template must have fullName column");
        assertTrue(template.contains("email"), "Template must have email column");
        assertTrue(template.contains("phone"), "Template must have phone column");
        assertTrue(template.contains("skillLevel"), "Template must have skillLevel column");
        assertTrue(template.contains("school"), "Template must have school column");
        assertTrue(template.contains("program"), "Template must have program column");
        assertTrue(template.contains("classGroup"), "Template must have classGroup column");
        assertTrue(template.contains("housingAssignment"), "Template must have housingAssignment column");
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("students.read", "students.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("students.read"),
                        new SimpleGrantedAuthority("students.write")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
