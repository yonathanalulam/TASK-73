package com.dojostay.ops;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.ops.dto.CreateExportJobRequest;
import com.dojostay.ops.dto.ExportJobResponse;
import com.dojostay.ops.dto.FeatureToggleResponse;
import com.dojostay.ops.dto.RecordBackupRequest;
import com.dojostay.ops.dto.UpsertFeatureToggleRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8: toggles upsert idempotently, backups record append-only, and
 * export jobs transition through the QUEUED → COMPLETED lifecycle.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class OpsReadinessIT {

    @Autowired private FeatureToggleService toggleService;
    @Autowired private BackupStatusService backupService;
    @Autowired private ExportJobService exportService;
    @Autowired private FeatureToggleRepository toggleRepository;
    @Autowired private BackupStatusRepository backupRepository;
    @Autowired private ExportJobRepository exportRepository;

    @BeforeEach
    void setUp() {
        exportRepository.deleteAll();
        backupRepository.deleteAll();
        toggleRepository.deleteAll();
        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void feature_toggle_upsert_is_idempotent_and_flips_state() {
        FeatureToggleResponse first = toggleService.upsert(
                new UpsertFeatureToggleRequest("lodging.v2", "New lodging UI", false),
                "127.0.0.1");
        assertNotNull(first.id());
        assertFalse(first.enabled());
        assertFalse(toggleService.isEnabled("lodging.v2"));

        FeatureToggleResponse flipped = toggleService.upsert(
                new UpsertFeatureToggleRequest("lodging.v2", "New lodging UI", true),
                "127.0.0.1");
        assertEquals(first.id(), flipped.id(), "upsert must not create a second row");
        assertTrue(flipped.enabled());
        assertTrue(toggleService.isEnabled("lodging.v2"));

        assertEquals(1, toggleRepository.count());
    }

    @Test
    void backup_records_append_only_and_list_is_newest_first() {
        backupService.record(new RecordBackupRequest(
                "DB_FULL", BackupStatus.Status.SUCCEEDED,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:05:00Z"),
                "s3://backups/2026-04-01.sql.gz", 1024L, 300_000L,
                null), "127.0.0.1");
        backupService.record(new RecordBackupRequest(
                "DB_FULL", BackupStatus.Status.SUCCEEDED,
                Instant.parse("2026-04-02T00:00:00Z"),
                Instant.parse("2026-04-02T00:04:00Z"),
                "s3://backups/2026-04-02.sql.gz", 1100L, 240_000L,
                null), "127.0.0.1");

        var recent = backupService.listRecent();
        assertEquals(2, recent.size());
        // Newest first.
        assertEquals(Instant.parse("2026-04-02T00:00:00Z"), recent.get(0).startedAt());
    }

    @Test
    void export_job_lifecycle_flows_from_queued_to_completed() {
        ExportJobResponse queued = exportService.request(
                new CreateExportJobRequest(null, "STUDENTS", ExportJob.Format.CSV),
                "127.0.0.1");
        assertEquals(ExportJob.Status.QUEUED, queued.status());

        ExportJobResponse completed = exportService.markCompleted(
                queued.id(), 42, "/tmp/exports/job-" + queued.id() + ".csv");
        assertEquals(ExportJob.Status.COMPLETED, completed.status());
        assertEquals(42, completed.rowCount());
        assertNotNull(completed.completedAt());

        List<ExportJobResponse> listed = exportService.listMine();
        assertEquals(1, listed.size());
        assertEquals(ExportJob.Status.COMPLETED, listed.get(0).status());
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("ops.toggles.read", "ops.toggles.write",
                        "ops.backups.read", "exports.read"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ops.toggles.read"),
                        new SimpleGrantedAuthority("ops.toggles.write"),
                        new SimpleGrantedAuthority("ops.backups.read"),
                        new SimpleGrantedAuthority("exports.read")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
