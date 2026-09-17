-- V9__create_audit_events_table.sql
-- Unified audit events table for persistent, queryable security and lifecycle audit trails.
--
-- NOTE: The legacy 'account_status_audit' table (created in V1 and adjusted in V6)
-- is intentionally preserved untouched as known cleanup debt and is superseded by this unified table.

CREATE TABLE audit_events (
    id BINARY(16) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    subject_user_id BINARY(16) NULL,
    actor_user_id BINARY(16) NULL,
    old_value VARCHAR(255) NULL,
    new_value VARCHAR(255) NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_events_subject_user FOREIGN KEY (subject_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_events_actor_user FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_events_event_type ON audit_events (event_type);
CREATE INDEX idx_audit_events_subject_user_id ON audit_events (subject_user_id);
CREATE INDEX idx_audit_events_actor_user_id ON audit_events (actor_user_id);
CREATE INDEX idx_audit_events_created_at ON audit_events (created_at);
