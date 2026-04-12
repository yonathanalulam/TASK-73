package com.dojostay.roles;

/**
 * Top-level role types from the spec. The fine-grained {@link Role} table can carry more
 * codes than this enum, but every user has exactly one primary role drawn from here, used
 * to drive the role-aware navigation shell.
 */
public enum UserRoleType {
    STUDENT,
    PHOTOGRAPHER,
    STAFF,
    ADMIN
}
