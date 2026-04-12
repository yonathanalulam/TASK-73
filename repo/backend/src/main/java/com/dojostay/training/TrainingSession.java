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
 * A scheduled instance of a {@link TrainingClass}. Bookings attach to a session,
 * not a class. Overlap detection for student double-booking uses
 * {@code [startsAt, endsAt)} half-open intervals.
 */
@Entity
@Table(name = "training_sessions")
public class TrainingSession {

    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    public enum SessionType { VENUE, ONLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "training_class_id", nullable = false)
    private Long trainingClassId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(length = 128)
    private String location;

    @Column(name = "instructor_user_id")
    private Long instructorUserId;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.SCHEDULED;

    @Column(length = 512)
    private String notes;

    // ---- Phase B6 sparring / matching filters ---------------------
    /** Venue (in-person at {@link #location}) vs. online (see {@link #onlineUrl}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", length = 16)
    private SessionType sessionType;

    /** Meeting URL for ONLINE sessions. Ignored for VENUE sessions. */
    @Column(name = "online_url", length = 512)
    private String onlineUrl;

    /** Target belt/skill level, free-form (e.g. "white", "blue-and-up"). */
    @Column(length = 32)
    private String level;

    /** Target weight class in pounds — nullable for unrestricted sessions. */
    @Column(name = "weight_class_lbs")
    private Integer weightClassLbs;

    /** Martial-arts style or discipline (e.g. "bjj", "muay-thai"). */
    @Column(length = 64)
    private String style;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getTrainingClassId() { return trainingClassId; }
    public void setTrainingClassId(Long trainingClassId) { this.trainingClassId = trainingClassId; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(Long instructorUserId) { this.instructorUserId = instructorUserId; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public SessionType getSessionType() { return sessionType; }
    public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }
    public String getOnlineUrl() { return onlineUrl; }
    public void setOnlineUrl(String onlineUrl) { this.onlineUrl = onlineUrl; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Integer getWeightClassLbs() { return weightClassLbs; }
    public void setWeightClassLbs(Integer weightClassLbs) { this.weightClassLbs = weightClassLbs; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
