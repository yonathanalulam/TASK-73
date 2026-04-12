-- V8: Phase 8 — feature toggles, backup status, export jobs.

CREATE TABLE feature_toggles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    updated_by_user_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_feature_toggles_code UNIQUE (code)
);

CREATE TABLE backup_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    location VARCHAR(512) NULL,
    size_bytes BIGINT NULL,
    duration_ms BIGINT NULL,
    notes VARCHAR(1000) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_backup_kind ON backup_statuses (kind);
CREATE INDEX idx_backup_started ON backup_statuses (started_at);

CREATE TABLE export_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NULL,
    requested_by_user_id BIGINT NOT NULL,
    kind VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    format VARCHAR(16) NOT NULL,
    row_count INT NULL,
    error_message VARCHAR(1000) NULL,
    artifact_path VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_export_org ON export_jobs (organization_id);
CREATE INDEX idx_export_requestor ON export_jobs (requested_by_user_id);
CREATE INDEX idx_export_status ON export_jobs (status);
