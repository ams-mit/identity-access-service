-- V6__add_rejected_status.sql
-- Expand status columns to VARCHAR(30) to accommodate REJECTED and future lifecycle statuses,
-- and align account_status_audit status columns with users.status length.

ALTER TABLE users
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION';

ALTER TABLE account_status_audit
    MODIFY COLUMN old_status VARCHAR(30) NULL,
    MODIFY COLUMN new_status VARCHAR(30) NOT NULL;
