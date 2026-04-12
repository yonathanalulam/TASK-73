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

/**
 * Records the execution of an ops job: scheduled backups, anomaly scans,
 * restore verification drills, or chaos/degradation drills. Each row is
 * a single execution event.
 */
@Entity
@Table(name = "ops_job_records")
public class OpsJobRecord {

    public enum JobKind { BACKUP, ANOMALY_SCAN, RESTORE_DRILL, CHAOS_DRILL }
    public enum JobStatus { STARTED, SUCCEEDED, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_kind", nullable = false, length = 32)
    private JobKind jobKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    /** Human-readable summary of what happened. */
    @Column(length = 2000)
    private String summary;

    /** Who or what triggered the job (e.g. "scheduler", "admin:user42"). */
    @Column(name = "triggered_by", length = 128)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public JobKind getJobKind() { return jobKind; }
    public void setJobKind(JobKind jobKind) { this.jobKind = jobKind; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public Instant getCreatedAt() { return createdAt; }
}
