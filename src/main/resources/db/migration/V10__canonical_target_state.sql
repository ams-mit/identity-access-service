-- V10__canonical_target_state.sql
-- Align identity-access-service schema with the Project A canonical contract.

-- Drop obsolete tables outside IAM scope
DROP TABLE IF EXISTS user_unit_relationships;
DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS audit_events;

-- Ensure the 9th canonical role SERVICE_STAFF is seeded
INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'SERVICE_STAFF', 'Service Staff'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SERVICE_STAFF');

-- Seed canonical administrative permissions
INSERT INTO permissions (id, code, description)
SELECT UUID_TO_BIN(UUID()), 'USER_MANAGE', 'Manage users'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'USER_MANAGE');

INSERT INTO permissions (id, code, description)
SELECT UUID_TO_BIN(UUID()), 'ROLE_MANAGE', 'Manage roles'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'ROLE_MANAGE');

INSERT INTO permissions (id, code, description)
SELECT UUID_TO_BIN(UUID()), 'PERMISSION_MANAGE', 'Manage permissions'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERMISSION_MANAGE');

-- Normalize legacy LOCKED status to canonical SUSPENDED
UPDATE users SET status = 'SUSPENDED' WHERE status = 'LOCKED';
