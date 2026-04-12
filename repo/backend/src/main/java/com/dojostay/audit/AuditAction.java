package com.dojostay.audit;

/**
 * Enumerates every audit action the application can record. Keeping these in a
 * single enum makes it possible to grep the codebase for every use of a given
 * action and keeps the audit grammar centralized. The DB column is a free-form
 * VARCHAR so adding new values does not require a migration.
 */
public enum AuditAction {

    // Phase 1 — authentication
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_LOGOUT,
    AUTH_ACCOUNT_LOCKED,
    AUTH_ACCOUNT_UNLOCKED,
    AUTH_PASSWORD_CHANGED,

    // Phase 2 — users, roles, scopes
    USER_CREATED,
    USER_UPDATED,
    USER_ENABLED,
    USER_DISABLED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    DATA_SCOPE_CHANGED,

    // Phase 3 — orgs, students, bulk import
    ORGANIZATION_CREATED,
    ORGANIZATION_UPDATED,
    STUDENT_CREATED,
    STUDENT_UPDATED,
    STUDENT_STATUS_CHANGED,
    BULK_IMPORT_RUN,

    // Phase 4 — property & lodging
    PROPERTY_CREATED,
    PROPERTY_UPDATED,
    PROPERTY_AMENITY_UPSERTED,
    PROPERTY_AMENITY_REMOVED,
    PROPERTY_IMAGE_UPLOADED,
    PROPERTY_IMAGE_DELETED,
    ROOM_TYPE_CREATED,
    ROOM_TYPE_UPDATED,
    NIGHTLY_RATE_UPSERTED,
    RESERVATION_CREATED,
    RESERVATION_CANCELLED,

    // Phase 5 — training, bookings, credits
    TRAINING_SESSION_CREATED,
    TRAINING_SESSION_CANCELLED,
    BOOKING_CREATED,
    BOOKING_CANCELLED,
    CREDIT_GRANTED,
    CREDIT_CONSUMED,

    // Phase 6 — community, notifications, moderation
    POST_CREATED,
    POST_HIDDEN,
    POST_RESTORED,
    COMMENT_CREATED,
    COMMENT_HIDDEN,
    COMMENT_RESTORED,
    POST_LIKED,
    POST_UNLIKED,
    USER_FOLLOWED,
    USER_UNFOLLOWED,
    USER_BLOCKED,
    USER_UNBLOCKED,
    THREAD_MUTED,
    THREAD_UNMUTED,
    MODERATION_REPORT_FILED,
    MODERATION_REPORT_RESOLVED,
    NOTIFICATION_SENT,

    // Phase 7 — credentials & risk
    CREDENTIAL_REVIEW_SUBMITTED,
    CREDENTIAL_REVIEW_APPROVED,
    CREDENTIAL_REVIEW_REJECTED,
    CREDENTIAL_FILE_DUPLICATE,
    USER_BLACKLISTED,
    RISK_FLAG_RAISED,
    RISK_FLAG_CLEARED,
    INCIDENT_LOGGED,

    // Phase 8 — ops readiness
    FEATURE_TOGGLE_CHANGED,
    BACKUP_RECORDED,
    EXPORT_REQUESTED
}
