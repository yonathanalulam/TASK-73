package com.dojostay.credentials;

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
 * A formal belt-rank credential request from a student. Reviews transition
 * {@code SUBMITTED → APPROVED|REJECTED}. A decided review is immutable; a new
 * request starts a new row.
 */
@Entity
@Table(name = "credential_reviews")
public class CredentialReview {

    public enum Status { SUBMITTED, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, length = 64)
    private String discipline;

    @Column(name = "requested_rank", nullable = false, length = 64)
    private String requestedRank;

    @Column(name = "current_rank", length = 64)
    private String currentRank;

    @Column(length = 2000)
    private String evidence;

    // ---- File upload pipeline (Phase A3) -----------------------
    /** Original filename as uploaded — kept for display only. */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Detected/declared MIME type; whitelist-enforced in the service. */
    @Column(name = "file_mime", length = 128)
    private String fileMime;

    /** File size in bytes. Service rejects anything above the configured cap. */
    @Column(name = "file_size")
    private Long fileSize;

    /** On-disk storage path for the uploaded artifact. */
    @Column(name = "file_storage_path", length = 512)
    private String fileStoragePath;

    /**
     * SHA-256 fingerprint (hex) of the file bytes. Used for duplicate detection:
     * if a second review is submitted with the same fingerprint (even by a
     * different student) the service rejects it as DUPLICATE_EVIDENCE so a
     * reused certificate cannot be laundered through multiple accounts.
     */
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.SUBMITTED;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getDiscipline() { return discipline; }
    public void setDiscipline(String discipline) { this.discipline = discipline; }
    public String getRequestedRank() { return requestedRank; }
    public void setRequestedRank(String requestedRank) { this.requestedRank = requestedRank; }
    public String getCurrentRank() { return currentRank; }
    public void setCurrentRank(String currentRank) { this.currentRank = currentRank; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public void setSubmittedByUserId(Long submittedByUserId) { this.submittedByUserId = submittedByUserId; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileMime() { return fileMime; }
    public void setFileMime(String fileMime) { this.fileMime = fileMime; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileStoragePath() { return fileStoragePath; }
    public void setFileStoragePath(String fileStoragePath) { this.fileStoragePath = fileStoragePath; }

    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
}
