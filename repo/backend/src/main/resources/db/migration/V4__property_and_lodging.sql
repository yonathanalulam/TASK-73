-- V4: Phase 4 — property / rooms / beds / reservations.

CREATE TABLE properties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(255) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_properties_code UNIQUE (organization_id, code)
);
CREATE INDEX idx_properties_org ON properties (organization_id);

CREATE TABLE rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    floor_label VARCHAR(32) NULL,
    capacity INT NOT NULL DEFAULT 1,
    active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_code UNIQUE (property_id, code)
);
CREATE INDEX idx_rooms_property ON rooms (property_id);

CREATE TABLE beds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(64) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_beds_code UNIQUE (room_id, code)
);
CREATE INDEX idx_beds_room ON beds (room_id);

CREATE TABLE lodging_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    student_id BIGINT NULL,
    guest_name VARCHAR(128) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes VARCHAR(512) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_lodging_bed ON lodging_reservations (bed_id);
CREATE INDEX idx_lodging_org ON lodging_reservations (organization_id);
CREATE INDEX idx_lodging_dates ON lodging_reservations (starts_on, ends_on);
CREATE INDEX idx_lodging_status ON lodging_reservations (status);
