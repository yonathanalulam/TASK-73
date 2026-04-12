package com.dojostay.ops;

import com.dojostay.common.ApiResponse;
import com.dojostay.ops.dto.BackupStatusResponse;
import com.dojostay.ops.dto.CreateExportJobRequest;
import com.dojostay.ops.dto.ExportJobResponse;
import com.dojostay.ops.dto.FeatureToggleResponse;
import com.dojostay.ops.dto.OpsJobRecordResponse;
import com.dojostay.ops.dto.RecordBackupRequest;
import com.dojostay.ops.dto.TriggerOpsJobRequest;
import com.dojostay.ops.dto.UpsertFeatureToggleRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Single controller exposing the three Phase 8 surfaces (toggles, backups,
 * exports). They share an {@code /api/ops} prefix and distinct permission
 * gates so a backup daemon only needs {@code ops.backups.read} and cannot
 * toggle features.
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final FeatureToggleService toggleService;
    private final BackupStatusService backupService;
    private final ExportJobService exportService;
    private final OpsJobService opsJobService;

    public OpsController(FeatureToggleService toggleService,
                         BackupStatusService backupService,
                         ExportJobService exportService,
                         OpsJobService opsJobService) {
        this.toggleService = toggleService;
        this.backupService = backupService;
        this.exportService = exportService;
        this.opsJobService = opsJobService;
    }

    @GetMapping("/toggles")
    @PreAuthorize("hasAuthority('ops.toggles.read')")
    public ApiResponse<List<FeatureToggleResponse>> listToggles() {
        return ApiResponse.ok(toggleService.list());
    }

    @PostMapping("/toggles")
    @PreAuthorize("hasAuthority('ops.toggles.write')")
    public ApiResponse<FeatureToggleResponse> upsertToggle(
            @Valid @RequestBody UpsertFeatureToggleRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(toggleService.upsert(req, clientIp(httpReq)));
    }

    @GetMapping("/backups")
    @PreAuthorize("hasAuthority('ops.backups.read')")
    public ApiResponse<List<BackupStatusResponse>> listBackups() {
        return ApiResponse.ok(backupService.listRecent());
    }

    @PostMapping("/backups")
    @PreAuthorize("hasAuthority('ops.backups.read')")
    public ApiResponse<BackupStatusResponse> recordBackup(
            @Valid @RequestBody RecordBackupRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(backupService.record(req, clientIp(httpReq)));
    }

    @GetMapping("/exports")
    @PreAuthorize("hasAuthority('exports.read')")
    public ApiResponse<List<ExportJobResponse>> listExports() {
        return ApiResponse.ok(exportService.listMine());
    }

    @PostMapping("/exports")
    @PreAuthorize("hasAuthority('exports.read')")
    public ApiResponse<ExportJobResponse> requestExport(
            @Valid @RequestBody CreateExportJobRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(exportService.request(req, clientIp(httpReq)));
    }

    // ---- Ops Job Records (backup runs, anomaly scans, drills) ---------

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('ops.backups.read')")
    public ApiResponse<List<OpsJobRecordResponse>> listOpsJobs() {
        return ApiResponse.ok(opsJobService.listAll());
    }

    @GetMapping("/jobs/{kind}")
    @PreAuthorize("hasAuthority('ops.backups.read')")
    public ApiResponse<List<OpsJobRecordResponse>> listOpsJobsByKind(
            @PathVariable OpsJobRecord.JobKind kind) {
        return ApiResponse.ok(opsJobService.listByKind(kind));
    }

    @PostMapping("/jobs/trigger")
    @PreAuthorize("hasAuthority('ops.toggles.write')")
    public ApiResponse<OpsJobRecordResponse> triggerOpsJob(
            @Valid @RequestBody TriggerOpsJobRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(opsJobService.triggerManual(req.jobKind(), clientIp(httpReq)));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
