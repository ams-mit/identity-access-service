-- V4__add_requested_role.sql
-- Add advisory requested_role column to users table for registration role selection.

ALTER TABLE users
    ADD COLUMN requested_role VARCHAR(50) NULL;
