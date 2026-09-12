-- V3__account_status_and_lockout.sql
-- Fix users.status values and defaults, add user profile columns, and add failed-login lockout tracking.

ALTER TABLE users
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    MODIFY COLUMN username VARCHAR(150) NULL,
    ADD COLUMN email VARCHAR(150) UNIQUE AFTER id,
    ADD COLUMN first_name VARCHAR(100) AFTER password_hash,
    ADD COLUMN last_name VARCHAR(100) AFTER first_name,
    ADD COLUMN phone VARCHAR(20) AFTER last_name,
    ADD COLUMN failed_attempt_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN locked_until TIMESTAMP NULL AFTER failed_attempt_count;
