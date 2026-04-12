-- V12: Make user_roles.assigned_at self-populate for join-table inserts.

ALTER TABLE user_roles
    MODIFY COLUMN assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
