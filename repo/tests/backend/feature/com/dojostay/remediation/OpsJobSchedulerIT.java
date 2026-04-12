package com.dojostay.remediation;

import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.ops.OpsJobRecord;
import com.dojostay.ops.OpsJobRecordRepository;
import com.dojostay.ops.OpsJobService;
import com.dojostay.ops.dto.OpsJobRecordResponse;
import com.dojostay.roles.UserRoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ops readiness scheduler-backed job records:
 * - scheduled backup creates record
 * - scheduled anomaly scan creates record
 * - scheduled restore drill creates record
 * - scheduled chaos drill creates record
 * - manual trigger creates record
 * - list endpoints return history
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OpsJobSchedulerIT {

    @Autowired private OpsJobService opsJobService;
    @Autowired private OpsJobRecordRepository recordRepository;

    @BeforeEach
    void setUp() {
        setCurrentUser(1L, "admin", UserRoleType.ADMIN);
    }

    @Test
    void scheduledBackupCreatesRecord() {
        opsJobService.scheduledBackup();

        List<OpsJobRecord> records = recordRepository
                .findTop20ByJobKindOrderByStartedAtDesc(OpsJobRecord.JobKind.BACKUP);
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getStatus()).isEqualTo(OpsJobRecord.JobStatus.SUCCEEDED);
        assertThat(records.get(0).getTriggeredBy()).isEqualTo("scheduler");
    }

    @Test
    void scheduledAnomalyScanCreatesRecord() {
        opsJobService.scheduledAnomalyScan();

        List<OpsJobRecord> records = recordRepository
                .findTop20ByJobKindOrderByStartedAtDesc(OpsJobRecord.JobKind.ANOMALY_SCAN);
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSummary()).isNotNull();
    }

    @Test
    void scheduledRestoreDrillCreatesRecord() {
        opsJobService.scheduledRestoreDrill();

        List<OpsJobRecord> records = recordRepository
                .findTop20ByJobKindOrderByStartedAtDesc(OpsJobRecord.JobKind.RESTORE_DRILL);
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSummary()).contains("PITR");
    }

    @Test
    void scheduledChaosDrillCreatesRecord() {
        opsJobService.scheduledChaosDrill();

        List<OpsJobRecord> records = recordRepository
                .findTop20ByJobKindOrderByStartedAtDesc(OpsJobRecord.JobKind.CHAOS_DRILL);
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSummary()).contains("Chaos drill");
    }

    @Test
    void manualTriggerCreatesRecord() {
        OpsJobRecordResponse response = opsJobService.triggerManual(
                OpsJobRecord.JobKind.BACKUP, "127.0.0.1");

        assertThat(response.id()).isNotNull();
        assertThat(response.jobKind()).isEqualTo("BACKUP");
        assertThat(response.triggeredBy()).contains("admin");
    }

    @Test
    void listAllReturnsHistory() {
        opsJobService.scheduledBackup();
        opsJobService.scheduledAnomalyScan();

        List<OpsJobRecordResponse> all = opsJobService.listAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void listByKindFiltersCorrectly() {
        opsJobService.scheduledBackup();
        opsJobService.scheduledAnomalyScan();

        List<OpsJobRecordResponse> backups = opsJobService
                .listByKind(OpsJobRecord.JobKind.BACKUP);
        assertThat(backups).allSatisfy(r ->
                assertThat(r.jobKind()).isEqualTo("BACKUP"));
    }

    private void setCurrentUser(Long id, String username, UserRoleType role) {
        CurrentUser cu = new CurrentUser(id, username, username, role, Set.of(role.name()),
                Set.of("ops.toggles.read", "ops.toggles.write", "ops.backups.read"));
        CurrentUserResolver.set(cu);
    }
}
