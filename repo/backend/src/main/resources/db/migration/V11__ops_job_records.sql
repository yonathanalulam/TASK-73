-- V11: Add ops job execution history for scheduled and manual drills.

CREATE TABLE ops_job_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    duration_ms BIGINT NULL,
    summary VARCHAR(2000) NULL,
    triggered_by VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ops_job_records_kind_started
    ON ops_job_records (job_kind, started_at);

CREATE INDEX idx_ops_job_records_started
    ON ops_job_records (started_at);
