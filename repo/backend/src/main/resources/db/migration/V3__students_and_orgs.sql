-- V3: Phase 3 — students, bulk import jobs, richer org metadata.

ALTER TABLE organizations ADD COLUMN contact_email VARCHAR(190) NULL;
ALTER TABLE organizations ADD COLUMN contact_phone VARCHAR(32) NULL;
ALTER TABLE organizations ADD COLUMN description VARCHAR(512) NULL;

CREATE TABLE students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    organization_id BIGINT NULL,
    external_id VARCHAR(64) NULL,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(190) NULL,
    phone VARCHAR(32) NULL,
    date_of_birth DATE NULL,
    emergency_contact_name VARCHAR(128) NULL,
    emergency_contact_phone VARCHAR(32) NULL,
    enrollment_status VARCHAR(32) NOT NULL,
    skill_level VARCHAR(32) NULL,
    notes VARCHAR(1024) NULL,
    enrolled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_students_external UNIQUE (organization_id, external_id)
);
CREATE INDEX idx_students_org ON students (organization_id);
CREATE INDEX idx_students_user ON students (user_id);
CREATE INDEX idx_students_status ON students (enrollment_status);

CREATE TABLE bulk_import_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(32) NOT NULL,
    organization_id BIGINT NULL,
    submitted_by_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    created_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    error_summary VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_bulk_import_kind ON bulk_import_jobs (kind);
