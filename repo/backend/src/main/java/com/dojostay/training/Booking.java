package com.dojostay.training;

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
 * A student's reservation of a seat in a specific {@link TrainingSession}.
 * Bookings are constrained by {@code PropertyService}-style conflict checks:
 * a student cannot have two non-cancelled bookings whose parent sessions
 * overlap in time.
 *
 * <p>Phase B6 introduces a new lifecycle for training/sparring bookings:
 * {@code INITIATED → CONFIRMED → (CANCELED → REFUNDED)}. New bookings start
 * as {@code INITIATED}; a reviewer or the self-service flow transitions them
 * to {@code CONFIRMED} once the seat is held. A cancelled booking can be
 * moved to {@code REFUNDED} once a matching credit-ledger entry has been
 * posted — the id of that entry is stored in {@code refundCreditTxId} so the
 * refund trail is end-to-end traceable. The legacy
 * {@code BOOKED/CHECKED_IN/COMPLETED/CANCELLED/NO_SHOW} values remain valid
 * for rows written before B6 so the history table does not need migrating.
 * Note the spelling: {@code CANCELED}/{@code CANCELLED} are distinct states
 * — the former is the new (American) spelling used by the B6 lifecycle and
 * the latter is preserved verbatim from the pre-B6 rows.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    public enum Status {
        // Phase B6 lifecycle
        INITIATED, CONFIRMED, CANCELED, REFUNDED,
        // Pre-B6 states — still valid for historical rows and completion flows
        BOOKED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW
    }

    public enum SessionType { VENUE, ONLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "training_session_id", nullable = false)
    private Long trainingSessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.INITIATED;

    /**
     * Venue vs. online. Null for legacy rows written before B6.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", length = 16)
    private SessionType sessionType;

    /**
     * Id of the {@link CreditTransaction} posted as the refund for this
     * booking. Only set when the booking has been moved to {@code REFUNDED}.
     * Not an FK because the ledger is append-only and we want to tolerate
     * ledger-table backfills without migration ordering pain.
     */
    @Column(name = "refund_credit_tx_id")
    private Long refundCreditTxId;

    @Column(length = 512)
    private String notes;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getTrainingSessionId() { return trainingSessionId; }
    public void setTrainingSessionId(Long trainingSessionId) { this.trainingSessionId = trainingSessionId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public SessionType getSessionType() { return sessionType; }
    public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }
    public Long getRefundCreditTxId() { return refundCreditTxId; }
    public void setRefundCreditTxId(Long refundCreditTxId) { this.refundCreditTxId = refundCreditTxId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
