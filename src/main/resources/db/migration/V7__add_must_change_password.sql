-- V7__add_must_change_password.sql
-- Add must_change_password column to users table to require password change on first login.

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
