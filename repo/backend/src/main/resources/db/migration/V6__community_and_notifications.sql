-- V6: Phase 6 — community feed, comments, notifications, moderation reports.

CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    title VARCHAR(160) NULL,
    body TEXT NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    hidden_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    hidden_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_posts_org ON posts (organization_id);
CREATE INDEX idx_posts_author ON posts (author_user_id);
CREATE INDEX idx_posts_status ON posts (status);

CREATE TABLE post_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    hidden_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    hidden_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_comments_post ON post_comments (post_id);
CREATE INDEX idx_comments_org ON post_comments (organization_id);
CREATE INDEX idx_comments_author ON post_comments (author_user_id);

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    organization_id BIGINT NULL,
    kind VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NULL,
    reference_type VARCHAR(64) NULL,
    reference_id VARCHAR(64) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id);
CREATE INDEX idx_notifications_org ON notifications (organization_id);
CREATE INDEX idx_notifications_unread ON notifications (recipient_user_id, read_at);

CREATE TABLE moderation_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    details VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL,
    resolution_notes VARCHAR(1000) NULL,
    reviewed_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_reports_org ON moderation_reports (organization_id);
CREATE INDEX idx_reports_target ON moderation_reports (target_type, target_id);
CREATE INDEX idx_reports_status ON moderation_reports (status);
