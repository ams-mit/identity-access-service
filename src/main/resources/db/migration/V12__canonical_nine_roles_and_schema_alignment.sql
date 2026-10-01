-- V12__canonical_nine_roles_and_schema_alignment.sql
-- Ensure active columns on roles and permissions, clean legacy roles, and ensure all 9 canonical roles exist.

-- 1. Ensure 'active' column exists on roles table
DELIMITER $$
DROP PROCEDURE IF EXISTS add_roles_active_column $$
CREATE PROCEDURE add_roles_active_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'roles' AND COLUMN_NAME = 'active'
    ) THEN
        ALTER TABLE roles ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
END $$
DELIMITER ;
CALL add_roles_active_column();
DROP PROCEDURE IF EXISTS add_roles_active_column;

-- 2. Ensure 'active' column exists on permissions table
DELIMITER $$
DROP PROCEDURE IF EXISTS add_permissions_active_column $$
CREATE PROCEDURE add_permissions_active_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'permissions' AND COLUMN_NAME = 'active'
    ) THEN
        ALTER TABLE permissions ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
END $$
DELIMITER ;
CALL add_permissions_active_column();
DROP PROCEDURE IF EXISTS add_permissions_active_column;

-- 3. Remap any legacy user_roles assignments to canonical roles if present
UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SYSTEM_ADMINISTRATOR'
SET ur.role_id = canon_r.id
WHERE legacy_r.name IN ('ADMIN', 'SYSTEM_ADMIN');

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'APARTMENT_MANAGER'
SET ur.role_id = canon_r.id
WHERE legacy_r.name IN ('MANAGER', 'PROPERTY_MANAGER');

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'TENANT_RESIDENT'
SET ur.role_id = canon_r.id
WHERE legacy_r.name IN ('TENANT', 'RESIDENT');

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'OWNER'
SET ur.role_id = canon_r.id
WHERE legacy_r.name = 'OWN';

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SECURITY_OFFICER'
SET ur.role_id = canon_r.id
WHERE legacy_r.name = 'SECURITY';

-- 4. Delete foreign key references for any non-canonical legacy roles
DELETE FROM user_roles WHERE role_id IN (
    SELECT id FROM roles WHERE name NOT IN (
        'SYSTEM_ADMINISTRATOR', 'APARTMENT_MANAGER', 'OWNER', 'TENANT_RESIDENT',
        'FINANCE_OFFICER', 'MAINTENANCE_COORDINATOR', 'TECHNICIAN', 'SERVICE_STAFF', 'SECURITY_OFFICER'
    )
);

DELETE FROM role_permissions WHERE role_id IN (
    SELECT id FROM roles WHERE name NOT IN (
        'SYSTEM_ADMINISTRATOR', 'APARTMENT_MANAGER', 'OWNER', 'TENANT_RESIDENT',
        'FINANCE_OFFICER', 'MAINTENANCE_COORDINATOR', 'TECHNICIAN', 'SERVICE_STAFF', 'SECURITY_OFFICER'
    )
);

-- 5. Delete any non-canonical legacy roles (ADMIN, MANAGER, TENANT, RESIDENT, OWN, etc.)
DELETE FROM roles WHERE name NOT IN (
    'SYSTEM_ADMINISTRATOR', 'APARTMENT_MANAGER', 'OWNER', 'TENANT_RESIDENT',
    'FINANCE_OFFICER', 'MAINTENANCE_COORDINATOR', 'TECHNICIAN', 'SERVICE_STAFF', 'SECURITY_OFFICER'
);

-- 6. Insert all 9 canonical roles if not present
INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'SYSTEM_ADMINISTRATOR', 'System Administrator with full management access', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SYSTEM_ADMINISTRATOR');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'APARTMENT_MANAGER', 'Apartment and Property Manager', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'APARTMENT_MANAGER');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'OWNER', 'Property Owner', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'OWNER');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'TENANT_RESIDENT', 'Tenant or Resident', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TENANT_RESIDENT');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'FINANCE_OFFICER', 'Finance Officer', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'FINANCE_OFFICER');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'MAINTENANCE_COORDINATOR', 'Maintenance Coordinator', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'MAINTENANCE_COORDINATOR');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'TECHNICIAN', 'Maintenance Technician', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TECHNICIAN');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'SERVICE_STAFF', 'Service and Support Staff', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SERVICE_STAFF');

INSERT INTO roles (id, name, description, active)
SELECT UUID_TO_BIN(UUID()), 'SECURITY_OFFICER', 'Security Officer', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SECURITY_OFFICER');

-- 7. Ensure active flag is TRUE for all 9 canonical roles
UPDATE roles SET active = TRUE WHERE name IN (
    'SYSTEM_ADMINISTRATOR', 'APARTMENT_MANAGER', 'OWNER', 'TENANT_RESIDENT',
    'FINANCE_OFFICER', 'MAINTENANCE_COORDINATOR', 'TECHNICIAN', 'SERVICE_STAFF', 'SECURITY_OFFICER'
);
