package com.dojostay.students;

/**
 * Lifecycle state of a student record. Stored as a VARCHAR in the database so that
 * adding a new state does not require a schema change.
 */
public enum EnrollmentStatus {
    PROSPECT,
    ACTIVE,
    PAUSED,
    GRADUATED,
    WITHDRAWN
}
