-- DojoStay V2: identity, RBAC resource layer, organization structure, data scope rules.
--
-- Introduces:
--   * organizations / departments / facility_areas   — reference hierarchy used by scopes
--   * resources                                      — lookup table for permission targets
--   * data_scope_rules                               — per-user scoping rows
--   * users.organization_id                          — lets users belong to an org (nullable)
--   * permissions.resource_code                      — optional tagging to a resource
--
-- Scoped query filtering in Phase 2 is proven against the users table itself: a staff
-- account scoped to organization A can only list users in organization A. Phases 3+ will
-- attach the same DataScopeSpec mechanism to students, bookings, etc.

CREATE TABLE organizations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    parent_id  BIGINT,
    active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uk_organizations_code UNIQUE (code),
    CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES organizations(id)
);

CREATE TABLE departments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uk_departments_org_code UNIQUE (organization_id, code),
    CONSTRAINT fk_dept_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE TABLE facility_areas (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    organization_id BIGINT       NOT NULL,
    department_id   BIGINT,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_facility_areas PRIMARY KEY (id),
    CONSTRAINT uk_facility_areas_org_code UNIQUE (organization_id, code),
    CONSTRAINT fk_fa_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_fa_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL
);

-- Reference list of resource "buckets" that permissions can be tagged with
-- (users, bookings, students, etc.). The resource_code column on permissions is a
-- soft reference (no FK) so new resources can be added without a migration just by
-- inserting rows here and in permissions.
CREATE TABLE resources (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description  VARCHAR(255),
    CONSTRAINT pk_resources PRIMARY KEY (id),
    CONSTRAINT uk_resources_code UNIQUE (code)
);

ALTER TABLE permissions ADD COLUMN resource_code VARCHAR(64);
CREATE INDEX idx_permissions_resource ON permissions (resource_code);

-- Link users to the organization they belong to. Nullable so existing admin accounts
-- (which have effective cross-org access) don't need an org assignment.
ALTER TABLE users ADD COLUMN organization_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);
CREATE INDEX idx_users_organization ON users (organization_id);

-- Per-user data scope rules. A user can have multiple rules; the effective scope is
-- the union of all rules at a given scope type.
--   scope_type: ORGANIZATION | DEPARTMENT | FACILITY_AREA
--   scope_target_id: id into the corresponding table
CREATE TABLE data_scope_rules (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    scope_type      VARCHAR(32)  NOT NULL,
    scope_target_id BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_data_scope_rules PRIMARY KEY (id),
    CONSTRAINT uk_data_scope_rules UNIQUE (user_id, scope_type, scope_target_id),
    CONSTRAINT fk_dsr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_dsr_user_type ON data_scope_rules (user_id, scope_type);
