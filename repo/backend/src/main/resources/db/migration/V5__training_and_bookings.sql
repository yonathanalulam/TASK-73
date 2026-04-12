-- V5: Phase 5 — training classes, sessions, bookings, credit ledger.

CREATE TABLE training_classes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    discipline VARCHAR(64) NULL,
    level VARCHAR(32) NULL,
    default_capacity INT NOT NULL DEFAULT 20,
    description VARCHAR(512) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_training_classes_code UNIQUE (organization_id, code)
);
CREATE INDEX idx_training_classes_org ON training_classes (organization_id);

CREATE TABLE training_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    training_class_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    location VARCHAR(128) NULL,
    instructor_user_id BIGINT NULL,
    capacity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_training_sessions_class ON training_sessions (training_class_id);
CREATE INDEX idx_training_sessions_org ON training_sessions (organization_id);
CREATE INDEX idx_training_sessions_times ON training_sessions (starts_at, ends_at);
CREATE INDEX idx_training_sessions_status ON training_sessions (status);

CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    training_session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes VARCHAR(512) NULL,
    checked_in_at DATETIME(6) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_bookings_session ON bookings (training_session_id);
CREATE INDEX idx_bookings_student ON bookings (student_id);
CREATE INDEX idx_bookings_org ON bookings (organization_id);
CREATE INDEX idx_bookings_status ON bookings (status);

CREATE TABLE credit_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    delta INT NOT NULL,
    balance_after INT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    reference_type VARCHAR(64) NULL,
    reference_id VARCHAR(64) NULL,
    notes VARCHAR(512) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_credit_tx_student ON credit_transactions (student_id);
CREATE INDEX idx_credit_tx_org ON credit_transactions (organization_id);
CREATE INDEX idx_credit_tx_reason ON credit_transactions (reason);
