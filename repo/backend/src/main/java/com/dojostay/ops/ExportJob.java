package com.dojostay.ops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "export_jobs")
public class ExportJob {

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    public enum Format { CSV, JSON }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(nullable = false, length = 64)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Format format = Format.CSV;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "artifact_path", length = 512)
    private String artifactPath;

    /**
     * Watermark string stamped on the export artifact when it is generated.
     * Set automatically at request time to {@code "<requester-username> <iso8601-timestamp>"}
     * so that every downloaded export carries proof-of-origin that can be traced
     * back to the authenticated user who requested it. The worker is expected
     * to render this into the artifact itself (header row for CSV, meta field
     * for JSON) — persisting it on the job row makes it auditable and lets the
     * client surface the mark without re-reading the artifact.
     */
    @Column(name = "watermark_text", length = 255)
    private String watermarkText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Format getFormat() { return format; }
    public void setFormat(Format format) { this.format = format; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
