-- DojoStay V1 baseline: identity, RBAC scaffolding, audit log.
-- This migration intentionally limits scope to Phase 1 (auth + lockout + audit).
-- Subsequent phases will add data scopes, organizations, students, etc.
--
-- DDL is kept portable across MySQL 8 and H2 (MODE=MySQL) so the same migration runs
-- in production and in the test profile. Indexes are declared with CREATE INDEX rather
-- than inline KEY clauses for the same reason.

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(128) NOT NULL,
    email           VARCHAR(190),
    primary_role    VARCHAR(32)  NOT NULL,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description  VARCHAR(255),
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(96)  NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description  VARCHAR(255),
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

CREATE TABLE user_roles (
    user_id     BIGINT       NOT NULL,
    role_id     BIGINT       NOT NULL,
    assigned_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_perms_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_perms_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE TABLE login_attempts (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    username       VARCHAR(64)  NOT NULL,
    successful     TINYINT(1)   NOT NULL,
    source_ip      VARCHAR(64),
    user_agent     VARCHAR(255),
    attempted_at   DATETIME(6)  NOT NULL,
    failure_reason VARCHAR(64),
    CONSTRAINT pk_login_attempts PRIMARY KEY (id)
);

CREATE INDEX idx_login_attempts_username_time ON login_attempts (username, attempted_at);

CREATE TABLE user_lock_states (
    user_id          BIGINT       NOT NULL,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     DATETIME(6),
    last_failed_at   DATETIME(6),
    last_unlocked_by BIGINT,
    last_unlocked_at DATETIME(6),
    CONSTRAINT pk_user_lock_states PRIMARY KEY (user_id),
    CONSTRAINT fk_user_lock_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Append-only audit log. No update/delete API will be exposed.
CREATE TABLE audit_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id   BIGINT,
    actor_username  VARCHAR(64),
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(64),
    target_id       VARCHAR(64),
    summary         VARCHAR(255),
    before_snapshot TEXT,
    after_snapshot  TEXT,
    correlation_id  VARCHAR(64),
    source_ip       VARCHAR(64),
    occurred_at     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_action_time ON audit_log (action, occurred_at);
CREATE INDEX idx_audit_actor       ON audit_log (actor_user_id, occurred_at);
CREATE INDEX idx_audit_target      ON audit_log (target_type, target_id);
