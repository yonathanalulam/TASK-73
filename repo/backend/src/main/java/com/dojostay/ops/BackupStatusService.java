package com.dojostay.ops;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.ops.dto.BackupStatusResponse;
import com.dojostay.ops.dto.RecordBackupRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records the outcome of backup jobs. This service does not perform backups
 * itself — it is a logbook that the out-of-process backup tool calls into via
 * the ops API to leave a breadcrumb trail that ops users can review.
 */
@Service
public class BackupStatusService {

    private final BackupStatusRepository repository;
    private final AuditService auditService;

    public BackupStatusService(BackupStatusRepository repository,
                               AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BackupStatusResponse> listRecent() {
        return repository.findTop20ByOrderByStartedAtDesc().stream()
                .map(BackupStatusService::toResponse)
                .toList();
    }

    @Transactional
    public BackupStatusResponse record(RecordBackupRequest req, String sourceIp) {
        BackupStatus b = new BackupStatus();
        b.setKind(req.kind());
        b.setStatus(req.status());
        b.setStartedAt(req.startedAt());
        b.setCompletedAt(req.completedAt());
        b.setLocation(req.location());
        b.setSizeBytes(req.sizeBytes());
        b.setDurationMs(req.durationMs());
        b.setNotes(req.notes());
        BackupStatus saved = repository.save(b);

        auditService.record(AuditAction.BACKUP_RECORDED, actorId(), actorUsername(),
                "BACKUP", String.valueOf(saved.getId()),
                req.kind() + " status=" + req.status(),
                sourceIp);
        return toResponse(saved);
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static BackupStatusResponse toResponse(BackupStatus b) {
        return new BackupStatusResponse(
                b.getId(), b.getKind(), b.getStatus(),
                b.getLocation(), b.getSizeBytes(), b.getDurationMs(),
                b.getNotes(), b.getStartedAt(), b.getCompletedAt()
        );
    }
}
