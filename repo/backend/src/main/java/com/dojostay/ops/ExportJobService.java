package com.dojostay.ops;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.ops.dto.CreateExportJobRequest;
import com.dojostay.ops.dto.ExportJobResponse;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Queues data export requests. Exports run out-of-process; this service only
 * tracks their lifecycle as rows in {@code export_jobs}. Callers submit via
 * {@link #request} and a worker eventually calls {@link #markCompleted} or
 * {@link #markFailed}. Scope filtering: users see only their own requests
 * unless they are in a fullAccess scope.
 */
@Service
public class ExportJobService {

    private final ExportJobRepository repository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public ExportJobService(ExportJobRepository repository,
                            DataScopeService dataScopeService,
                            AuditService auditService) {
        this.repository = repository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ExportJobResponse> listMine() {
        Long me = requireActor();
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) {
            return repository.findAll().stream()
                    .map(ExportJobService::toResponse)
                    .toList();
        }
        return repository.findByRequestedByUserIdOrderByIdDesc(me).stream()
                .map(ExportJobService::toResponse)
                .toList();
    }

    @Transactional
    public ExportJobResponse request(CreateExportJobRequest req, String sourceIp) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (req.organizationId() != null
                && !scope.fullAccess()
                && !scope.hasOrganization(req.organizationId())) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Target organization is not in your data scope",
                    HttpStatus.FORBIDDEN);
        }
        ExportJob job = new ExportJob();
        job.setOrganizationId(req.organizationId());
        job.setRequestedByUserId(requireActor());
        job.setKind(req.kind());
        job.setFormat(req.format());
        job.setStatus(ExportJob.Status.QUEUED);
        job.setWatermarkText(buildWatermark(actorUsername(), Instant.now()));
        ExportJob saved = repository.save(job);

        auditService.record(AuditAction.EXPORT_REQUESTED, actorId(), actorUsername(),
                "EXPORT_JOB", String.valueOf(saved.getId()),
                "kind=" + req.kind() + " format=" + req.format()
                        + " watermark=" + saved.getWatermarkText(),
                sourceIp);
        return toResponse(saved);
    }

    /**
     * Build the proof-of-origin watermark that will be stamped onto the
     * artifact. Format: {@code "<username> <ISO8601 instant>"}. Kept short
     * enough to fit the 255-char column and safe to render inline in CSV/JSON.
     */
    static String buildWatermark(String username, Instant when) {
        String safeUser = username == null ? "system" : username;
        return safeUser + " " + DateTimeFormatter.ISO_INSTANT.format(when);
    }

    @Transactional
    public ExportJobResponse markCompleted(Long jobId, int rowCount, String artifactPath) {
        ExportJob job = repository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Export job not found"));
        job.setStatus(ExportJob.Status.COMPLETED);
        job.setRowCount(rowCount);
        job.setArtifactPath(artifactPath);
        job.setCompletedAt(Instant.now());
        return toResponse(repository.save(job));
    }

    @Transactional
    public ExportJobResponse markFailed(Long jobId, String errorMessage) {
        ExportJob job = repository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Export job not found"));
        job.setStatus(ExportJob.Status.FAILED);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(Instant.now());
        return toResponse(repository.save(job));
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static Long requireActor() {
        Long id = actorId();
        if (id == null) {
            throw new BusinessException("NO_ACTOR",
                    "An authenticated user is required",
                    HttpStatus.UNAUTHORIZED);
        }
        return id;
    }

    private static ExportJobResponse toResponse(ExportJob j) {
        return new ExportJobResponse(
                j.getId(), j.getOrganizationId(), j.getRequestedByUserId(),
                j.getKind(), j.getStatus(), j.getFormat(), j.getRowCount(),
                j.getErrorMessage(), j.getArtifactPath(), j.getWatermarkText(),
                j.getCreatedAt(), j.getCompletedAt()
        );
    }
}
