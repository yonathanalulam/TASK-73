package com.dojostay.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Durable record of a bulk-import run. We keep a row even for failed imports so
 * administrators can audit what was attempted and who submitted it.
 */
@Entity
@Table(name = "bulk_import_jobs")
public class BulkImportJob {

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "created_rows", nullable = false)
    private int createdRows;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "error_summary", length = 2000)
    private String errorSummary;

    /**
     * Path to a CSV artifact containing one row per rejected input row.
     * Null until the import has finished; never populated for imports that
     * had zero errors (to avoid empty artifacts).
     */
    @Column(name = "error_report_path", length = 512)
    private String errorReportPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public Long getSubmittedByUserId() { return submittedByUserId; }
    public void setSubmittedByUserId(Long submittedByUserId) { this.submittedByUserId = submittedByUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setStatus(Status status) { this.status = status.name(); }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getCreatedRows() { return createdRows; }
    public void setCreatedRows(int createdRows) { this.createdRows = createdRows; }

    public int getSkippedRows() { return skippedRows; }
    public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }

    public int getFailedRows() { return failedRows; }
    public void setFailedRows(int failedRows) { this.failedRows = failedRows; }

    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }

    public String getErrorReportPath() { return errorReportPath; }
    public void setErrorReportPath(String errorReportPath) { this.errorReportPath = errorReportPath; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
