-- V7: Phase 7 — belt credentials, risk flags, incident log.

CREATE TABLE credential_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    discipline VARCHAR(64) NOT NULL,
    requested_rank VARCHAR(64) NOT NULL,
    current_rank VARCHAR(64) NULL,
    evidence VARCHAR(2000) NULL,
    status VARCHAR(32) NOT NULL,
    review_notes VARCHAR(1000) NULL,
    submitted_by_user_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_creview_org ON credential_reviews (organization_id);
CREATE INDEX idx_creview_student ON credential_reviews (student_id);
CREATE INDEX idx_creview_status ON credential_reviews (status);

CREATE TABLE risk_flags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL,
    raised_by_user_id BIGINT NOT NULL,
    cleared_by_user_id BIGINT NULL,
    clearance_notes VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    cleared_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_risk_org ON risk_flags (organization_id);
CREATE INDEX idx_risk_subject ON risk_flags (subject_type, subject_id);
CREATE INDEX idx_risk_status ON risk_flags (status);

CREATE TABLE incident_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NULL,
    subject_id BIGINT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    follow_up VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_incident_org ON incident_log (organization_id);
CREATE INDEX idx_incident_subject ON incident_log (subject_type, subject_id);
CREATE INDEX idx_incident_category ON incident_log (category);
