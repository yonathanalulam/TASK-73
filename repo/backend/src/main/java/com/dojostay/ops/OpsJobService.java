package com.dojostay.ops;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.ops.dto.OpsJobRecordResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduler-backed ops readiness service. Automatically runs periodic jobs for:
 * <ul>
 *   <li>Backup record creation (simulated — records a SUCCEEDED backup event)</li>
 *   <li>Anomaly scan (checks for basic threshold violations)</li>
 *   <li>Restore verification drill (simulated point-in-time restore test)</li>
 *   <li>Chaos/degradation drill (simulated)</li>
 * </ul>
 *
 * Each scheduled run creates an {@link OpsJobRecord} so ops staff can inspect
 * history via the API. Manual trigger is also available.
 */
@Service
public class OpsJobService {

    private static final Logger log = LoggerFactory.getLogger(OpsJobService.class);

    private final OpsJobRecordRepository recordRepository;
    private final BackupStatusRepository backupStatusRepository;
    private final AuditService auditService;

    public OpsJobService(OpsJobRecordRepository recordRepository,
                         BackupStatusRepository backupStatusRepository,
                         AuditService auditService) {
        this.recordRepository = recordRepository;
        this.backupStatusRepository = backupStatusRepository;
        this.auditService = auditService;
    }

    // ---- Scheduled jobs --------------------------------------------------

    /** Runs every 6 hours: records a simulated backup event. */
    @Scheduled(fixedDelayString = "${dojostay.ops.backup-interval-ms:21600000}")
    public void scheduledBackup() {
        runJob(OpsJobRecord.JobKind.BACKUP, "scheduler",
                "Scheduled backup simulation completed successfully");
    }

    /** Runs every 2 hours: scans for anomalies (e.g. no recent backup). */
    @Scheduled(fixedDelayString = "${dojostay.ops.anomaly-scan-interval-ms:7200000}")
    public void scheduledAnomalyScan() {
        Instant cutoff = Instant.now().minusSeconds(86400); // 24h
        long recentBackups = backupStatusRepository.findTop20ByOrderByStartedAtDesc().stream()
                .filter(b -> b.getStartedAt().isAfter(cutoff))
                .count();
        String summary = recentBackups > 0
                ? "Anomaly scan passed: " + recentBackups + " backups in last 24h"
                : "ALERT: No backups found in last 24 hours";
        OpsJobRecord.JobStatus status = recentBackups > 0
                ? OpsJobRecord.JobStatus.SUCCEEDED
                : OpsJobRecord.JobStatus.FAILED;
        runJob(OpsJobRecord.JobKind.ANOMALY_SCAN, "scheduler", summary, status);
    }

    /** Runs every 12 hours: simulated restore verification drill. */
    @Scheduled(fixedDelayString = "${dojostay.ops.restore-drill-interval-ms:43200000}")
    public void scheduledRestoreDrill() {
        runJob(OpsJobRecord.JobKind.RESTORE_DRILL, "scheduler",
                "PITR restore drill: simulated restore to latest checkpoint succeeded");
    }

    /** Runs every 24 hours: simulated chaos/degradation drill. */
    @Scheduled(fixedDelayString = "${dojostay.ops.chaos-drill-interval-ms:86400000}")
    public void scheduledChaosDrill() {
        runJob(OpsJobRecord.JobKind.CHAOS_DRILL, "scheduler",
                "Chaos drill: simulated service degradation and recovery completed");
    }

    // ---- Manual trigger --------------------------------------------------

    @Transactional
    public OpsJobRecordResponse triggerManual(OpsJobRecord.JobKind kind, String sourceIp) {
        String actor = CurrentUserResolver.current()
                .map(u -> "admin:" + u.username())
                .orElse("manual");
        OpsJobRecord record = runJob(kind, actor,
                "Manual " + kind.name().toLowerCase().replace('_', ' ') + " triggered by " + actor);

        auditService.record(AuditAction.BACKUP_RECORDED, actorId(), actorUsername(),
                "OPS_JOB", String.valueOf(record.getId()),
                "Manual ops job triggered: " + kind, sourceIp);
        return toResponse(record);
    }

    // ---- Query -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OpsJobRecordResponse> listAll() {
        return recordRepository.findTop50ByOrderByStartedAtDesc().stream()
                .map(OpsJobService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpsJobRecordResponse> listByKind(OpsJobRecord.JobKind kind) {
        return recordRepository.findTop20ByJobKindOrderByStartedAtDesc(kind).stream()
                .map(OpsJobService::toResponse)
                .toList();
    }

    // ---- Helpers ---------------------------------------------------------

    private OpsJobRecord runJob(OpsJobRecord.JobKind kind, String triggeredBy, String summary) {
        return runJob(kind, triggeredBy, summary, OpsJobRecord.JobStatus.SUCCEEDED);
    }

    private OpsJobRecord runJob(OpsJobRecord.JobKind kind, String triggeredBy,
                                String summary, OpsJobRecord.JobStatus status) {
        Instant start = Instant.now();
        OpsJobRecord record = new OpsJobRecord();
        record.setJobKind(kind);
        record.setStatus(status);
        record.setStartedAt(start);
        record.setCompletedAt(Instant.now());
        record.setDurationMs(Instant.now().toEpochMilli() - start.toEpochMilli());
        record.setSummary(summary);
        record.setTriggeredBy(triggeredBy);
        OpsJobRecord saved = recordRepository.save(record);
        log.info("[ops] {} job completed: status={} summary={}", kind, status, summary);
        return saved;
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    static OpsJobRecordResponse toResponse(OpsJobRecord r) {
        return new OpsJobRecordResponse(
                r.getId(), r.getJobKind().name(), r.getStatus().name(),
                r.getStartedAt(), r.getCompletedAt(), r.getDurationMs(),
                r.getSummary(), r.getTriggeredBy(), r.getCreatedAt());
    }
}
