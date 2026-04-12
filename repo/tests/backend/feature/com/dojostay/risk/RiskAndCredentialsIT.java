package com.dojostay.risk;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.credentials.CredentialReview;
import com.dojostay.credentials.CredentialReviewRepository;
import com.dojostay.credentials.CredentialReviewService;
import com.dojostay.credentials.dto.DecideCredentialReviewRequest;
import com.dojostay.credentials.dto.SubmitCredentialReviewRequest;
import com.dojostay.risk.dto.ClearRiskFlagRequest;
import com.dojostay.risk.dto.LogIncidentRequest;
import com.dojostay.risk.dto.RaiseRiskFlagRequest;
import com.dojostay.roles.UserRoleType;
import com.dojostay.students.EnrollmentStatus;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7 guarantees:
 *
 * <ul>
 *   <li>A decided credential review is immutable — re-deciding throws
 *       {@code REVIEW_ALREADY_DECIDED}.</li>
 *   <li>Risk flags transition open→cleared exactly once; a second clear is
 *       rejected with {@code ALREADY_CLEARED}.</li>
 *   <li>Incident log is append-only — logging a follow-up creates a new row,
 *       the original stays untouched.</li>
 * </ul>
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class RiskAndCredentialsIT {

    private static final Long ORG_A = 1100L;

    @Autowired private CredentialReviewService credentialReviewService;
    @Autowired private RiskService riskService;
    @Autowired private CredentialReviewRepository credentialReviewRepository;
    @Autowired private RiskFlagRepository riskFlagRepository;
    @Autowired private IncidentLogRepository incidentLogRepository;
    @Autowired private StudentRepository studentRepository;

    private Student alice;

    @BeforeEach
    void setUp() {
        credentialReviewRepository.deleteAll();
        riskFlagRepository.deleteAll();
        incidentLogRepository.deleteAll();
        studentRepository.deleteAll();

        alice = new Student();
        alice.setOrganizationId(ORG_A);
        alice.setFullName("Alice");
        alice.setEnrollmentStatus(EnrollmentStatus.ACTIVE);
        studentRepository.save(alice);

        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void decided_credential_review_cannot_be_redecided() {
        var submitted = credentialReviewService.submit(
                new SubmitCredentialReviewRequest(alice.getId(), "JIU_JITSU",
                        "BLUE", "WHITE", "3 years training"),
                "127.0.0.1");

        credentialReviewService.decide(submitted.id(),
                new DecideCredentialReviewRequest(
                        CredentialReview.Status.APPROVED, "Ready"),
                "127.0.0.1");

        var ex = assertThrows(BusinessException.class, () ->
                credentialReviewService.decide(submitted.id(),
                        new DecideCredentialReviewRequest(
                                CredentialReview.Status.REJECTED, "changed mind"),
                        "127.0.0.1"));
        assertEquals("REVIEW_ALREADY_DECIDED", ex.getCode());
    }

    @Test
    void risk_flag_can_be_cleared_only_once() {
        var raised = riskService.raiseFlag(
                new RaiseRiskFlagRequest(ORG_A,
                        RiskFlag.SubjectType.STUDENT, alice.getId(),
                        "MISSED_WAIVER", RiskFlag.Severity.MEDIUM,
                        "Waiver missing since enrollment"),
                "127.0.0.1");

        riskService.clearFlag(raised.id(),
                new ClearRiskFlagRequest("waiver on file"),
                "127.0.0.1");

        var ex = assertThrows(BusinessException.class, () ->
                riskService.clearFlag(raised.id(),
                        new ClearRiskFlagRequest("already clear"),
                        "127.0.0.1"));
        assertEquals("ALREADY_CLEARED", ex.getCode());

        assertEquals(1, riskService.listFlags().size());
    }

    @Test
    void incident_log_is_append_only() {
        var first = riskService.logIncident(
                new LogIncidentRequest(ORG_A, Instant.parse("2026-03-01T09:00:00Z"),
                        RiskFlag.SubjectType.STUDENT, alice.getId(),
                        "INJURY", RiskFlag.Severity.LOW,
                        "Sprained wrist during sparring", null),
                "127.0.0.1");
        assertNotNull(first.id());

        var followUp = riskService.logIncident(
                new LogIncidentRequest(ORG_A, Instant.parse("2026-03-03T10:00:00Z"),
                        RiskFlag.SubjectType.STUDENT, alice.getId(),
                        "INJURY_FOLLOWUP", RiskFlag.Severity.LOW,
                        "Medically cleared to return",
                        "Follow-up to incident #" + first.id()),
                "127.0.0.1");
        assertNotNull(followUp.id());

        List<IncidentLog> rows = incidentLogRepository.findAll();
        assertEquals(2, rows.size());
        // Original is untouched — no hidden mutation of the description.
        assertTrue(rows.stream().anyMatch(r ->
                "Sprained wrist during sparring".equals(r.getDescription())));
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("credentials.review", "risk.read", "risk.write",
                        "students.read", "students.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("credentials.review"),
                        new SimpleGrantedAuthority("risk.read"),
                        new SimpleGrantedAuthority("risk.write"),
                        new SimpleGrantedAuthority("students.read"),
                        new SimpleGrantedAuthority("students.write")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
