package com.dojostay.remediation;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.ops.ExportJob;
import com.dojostay.ops.ExportJobService;
import com.dojostay.ops.dto.CreateExportJobRequest;
import com.dojostay.ops.dto.ExportJobResponse;
import com.dojostay.ops.ExportJobRepository;
import com.dojostay.roles.UserRoleType;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D11 — A4 export watermark test.
 * Verifies that export jobs carry a watermark in the format "username ISO8601".
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class ExportWatermarkIT {

    @Autowired private ExportJobService exportJobService;
    @Autowired private ExportJobRepository exportJobRepo;

    @BeforeEach
    void setUp() {
        exportJobRepo.deleteAll();
        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void export_request_carries_watermark_with_username_and_timestamp() {
        CreateExportJobRequest req = new CreateExportJobRequest(
                null, "STUDENTS_CSV", ExportJob.Format.CSV);
        ExportJobResponse job = exportJobService.request(req, "127.0.0.1");

        assertNotNull(job.watermarkText(), "Export job must include watermark text");
        assertTrue(job.watermarkText().startsWith("export-admin "),
                "Watermark should start with the actor's username");
        // The rest is an ISO-8601 instant — just check it has 'T' and 'Z'
        String timestamp = job.watermarkText().substring("export-admin ".length());
        assertTrue(timestamp.contains("T") && timestamp.contains("Z"),
                "Watermark timestamp should be ISO-8601 format");
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "export-admin", "Export Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("ops.export"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ops.export")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
