-- V9: Phase 9 remediation — sensitive-field encryption column widening,
-- student master data expansion (school/program/class group/housing),
-- credential review file upload pipeline (SHA-256 fingerprint, mime, size,
-- storage path, blacklist flag), export watermark text, training booking
-- lifecycle + matching filters + refund ledger link, and property amenities
-- / images / policies / nightly rate calendar tables.
--
-- All changes are additive: existing rows remain valid. Columns widened for
-- ciphertext base64 envelopes (AES-GCM-256 produces ~1.37× the plaintext
-- size after base64).

-- ---- sensitive-field column widening for AES-GCM ciphertext ----------
ALTER TABLE students MODIFY COLUMN external_id VARCHAR(512) NULL;
ALTER TABLE students MODIFY COLUMN email VARCHAR(512) NULL;
ALTER TABLE students MODIFY COLUMN phone VARCHAR(512) NULL;
ALTER TABLE students MODIFY COLUMN emergency_contact_phone VARCHAR(512) NULL;

-- ---- student master data expansion (Phase B5) ------------------------
ALTER TABLE students ADD COLUMN school VARCHAR(128) NULL;
ALTER TABLE students ADD COLUMN program VARCHAR(128) NULL;
ALTER TABLE students ADD COLUMN class_group VARCHAR(64) NULL;
ALTER TABLE students ADD COLUMN housing_assignment VARCHAR(128) NULL;

-- Persisted error-report artifact for bulk imports: the service writes a
-- CSV with one row per rejected input row so an admin can download the
-- full list instead of relying on the 20-row sample in the API envelope.
ALTER TABLE bulk_import_jobs ADD COLUMN error_report_path VARCHAR(512) NULL;

-- ---- credential review file upload pipeline (Phase A3) ---------------
ALTER TABLE credential_reviews ADD COLUMN file_name VARCHAR(255) NULL;
ALTER TABLE credential_reviews ADD COLUMN file_mime VARCHAR(128) NULL;
ALTER TABLE credential_reviews ADD COLUMN file_size BIGINT NULL;
ALTER TABLE credential_reviews ADD COLUMN file_storage_path VARCHAR(512) NULL;
ALTER TABLE credential_reviews ADD COLUMN file_sha256 VARCHAR(64) NULL;
CREATE INDEX idx_creview_sha256 ON credential_reviews (file_sha256);

-- Blacklist flag on users so rejected credentials can disable an account
-- permanently and anchor a risk-control breadcrumb. Auth will reject login
-- attempts on blacklisted accounts in addition to the existing enabled flag.
ALTER TABLE users ADD COLUMN blacklisted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN blacklist_reason VARCHAR(512) NULL;
ALTER TABLE users ADD COLUMN blacklisted_at DATETIME(6) NULL;

-- ---- export job watermark (Phase A4) ---------------------------------
ALTER TABLE export_jobs ADD COLUMN watermark_text VARCHAR(255) NULL;

-- ---- training booking lifecycle + sparring semantics (Phase B6) ------
-- New lifecycle states:
--   INITIATED, CONFIRMED, CANCELED, REFUNDED
-- The existing BOOKED/CHECKED_IN/COMPLETED/CANCELLED/NO_SHOW values in the
-- `status` column remain valid for historical rows. We widen the column so
-- both grammars can coexist; the service layer is what enforces transitions.
ALTER TABLE bookings MODIFY COLUMN status VARCHAR(32) NOT NULL;
ALTER TABLE bookings ADD COLUMN session_type VARCHAR(16) NULL;
ALTER TABLE bookings ADD COLUMN refund_credit_tx_id BIGINT NULL;

-- Matching filters for sparring
ALTER TABLE training_sessions ADD COLUMN session_type VARCHAR(16) NULL;
ALTER TABLE training_sessions ADD COLUMN online_url VARCHAR(512) NULL;
ALTER TABLE training_sessions ADD COLUMN level VARCHAR(32) NULL;
ALTER TABLE training_sessions ADD COLUMN weight_class_lbs INT NULL;
ALTER TABLE training_sessions ADD COLUMN style VARCHAR(64) NULL;

-- ---- property richness (Phase B7) ------------------------------------
ALTER TABLE properties ADD COLUMN description VARCHAR(2000) NULL;
ALTER TABLE properties ADD COLUMN policies VARCHAR(4000) NULL;

CREATE TABLE property_amenities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    icon VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_property_amenity UNIQUE (property_id, code)
);
CREATE INDEX idx_property_amenities_prop ON property_amenities (property_id);

CREATE TABLE property_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    caption VARCHAR(255) NULL,
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_property_images_prop ON property_images (property_id, display_order);

-- Room types: group beds by room type for comparison; stores nightly base
-- rate, occupancy, and a comparison-friendly feature summary.
CREATE TABLE room_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(2000) NULL,
    max_occupancy INT NOT NULL DEFAULT 1,
    base_rate_cents INT NOT NULL DEFAULT 0,
    features VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_room_type_code UNIQUE (property_id, code)
);
CREATE INDEX idx_room_types_prop ON room_types (property_id);

-- Nightly rate calendar: per-room-type, per-day override of the base rate.
-- A missing row means "use base_rate_cents from room_types".
CREATE TABLE nightly_rates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    rate_cents INT NOT NULL,
    available_count INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_nightly_rate UNIQUE (room_type_id, stay_date)
);
CREATE INDEX idx_nightly_rates_room_date ON nightly_rates (room_type_id, stay_date);

-- ---- community extensions (Phase C8) ---------------------------------
ALTER TABLE post_comments ADD COLUMN parent_comment_id BIGINT NULL;
ALTER TABLE post_comments ADD COLUMN quoted_comment_id BIGINT NULL;
CREATE INDEX idx_post_comments_parent ON post_comments (parent_comment_id);

CREATE TABLE post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NULL,
    comment_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_like UNIQUE (post_id, comment_id, user_id)
);
CREATE INDEX idx_post_likes_user ON post_likes (user_id);

CREATE TABLE user_follows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    follower_user_id BIGINT NOT NULL,
    followed_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_follow UNIQUE (follower_user_id, followed_user_id)
);
CREATE INDEX idx_user_follows_followed ON user_follows (followed_user_id);

CREATE TABLE post_mentions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NULL,
    comment_id BIGINT NULL,
    mentioned_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_post_mentions_user ON post_mentions (mentioned_user_id);

CREATE TABLE thread_mutes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_thread_mute UNIQUE (user_id, post_id)
);

CREATE TABLE user_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_block UNIQUE (user_id, blocked_user_id)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_user_id);

-- Moderation restore needs to remember the prior hidden state so an UPHELD-
-- then-restored item returns to PUBLISHED, not some defaulted state.
ALTER TABLE posts ADD COLUMN restored_at DATETIME(6) NULL;
ALTER TABLE post_comments ADD COLUMN restored_at DATETIME(6) NULL;
