package com.dojostay.students;

import com.dojostay.common.security.SensitiveStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optional link to a {@code users} row — students who can log in. */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Organization the student belongs to. Nullable at the column level because the
     * phase-3 import job may park orphan rows while an admin reviews them, but the
     * read/write API always requires one for scope filtering to work.
     */
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "facility_area_id")
    private Long facilityAreaId;

    /**
     * External id from the importing system (school, registrar, legacy DB).
     * Encrypted at rest because these identifiers are often the student's
     * national id / school id number, which qualifies as sensitive. Column
     * widened to 512 to accommodate the base64 ciphertext envelope.
     */
    @Column(name = "external_id", length = 512)
    @Convert(converter = SensitiveStringConverter.class)
    private String externalId;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    /**
     * Email — encrypted at rest and masked on read by default. Widened from
     * 190 → 512 for ciphertext.
     */
    @Column(length = 512)
    @Convert(converter = SensitiveStringConverter.class)
    private String email;

    /**
     * Phone — encrypted at rest. Widened to 512 for ciphertext envelope.
     */
    @Column(length = 512)
    @Convert(converter = SensitiveStringConverter.class)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "emergency_contact_name", length = 128)
    private String emergencyContactName;

    /** Emergency contact phone — encrypted at rest. */
    @Column(name = "emergency_contact_phone", length = 512)
    @Convert(converter = SensitiveStringConverter.class)
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 32)
    private EnrollmentStatus enrollmentStatus = EnrollmentStatus.PROSPECT;

    @Column(name = "skill_level", length = 32)
    private String skillLevel;

    @Column(length = 1024)
    private String notes;

    // ---- Phase B5 master data -------------------------------------
    /** School the student attends (e.g. "Lincoln High School"). */
    @Column(length = 128)
    private String school;

    /** Program / track the student is enrolled in. */
    @Column(length = 128)
    private String program;

    /** Class group / cohort label. */
    @Column(name = "class_group", length = 64)
    private String classGroup;

    /**
     * Housing assignment — free-form but typically references a property+room
     * code (e.g. "HQ-DORM/101"). Not a FK so we can represent off-site housing
     * that the system does not manage.
     */
    @Column(name = "housing_assignment", length = 128)
    private String housingAssignment;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public Long getFacilityAreaId() { return facilityAreaId; }
    public void setFacilityAreaId(Long facilityAreaId) { this.facilityAreaId = facilityAreaId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public EnrollmentStatus getEnrollmentStatus() { return enrollmentStatus; }
    public void setEnrollmentStatus(EnrollmentStatus enrollmentStatus) {
        this.enrollmentStatus = enrollmentStatus;
    }

    public String getSkillLevel() { return skillLevel; }
    public void setSkillLevel(String skillLevel) { this.skillLevel = skillLevel; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }

    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }

    public String getClassGroup() { return classGroup; }
    public void setClassGroup(String classGroup) { this.classGroup = classGroup; }

    public String getHousingAssignment() { return housingAssignment; }
    public void setHousingAssignment(String housingAssignment) { this.housingAssignment = housingAssignment; }

    public Instant getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
