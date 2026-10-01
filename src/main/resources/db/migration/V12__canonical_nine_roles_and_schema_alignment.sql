-- V12__canonical_nine_roles_and_schema_alignment.sql
-- Ensure active columns on roles and permissions, safely migrate legacy role assignments,
-- remove legacy roles, and ensure all 9 canonical roles exist and are active.

-- 1. Ensure 'active' column exists on roles table using MySQL 8 information_schema check & dynamic SQL
SET @dbname = DATABASE();

SET @col_roles_active = (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = @dbname 
      AND TABLE_NAME = 'roles' 
      AND COLUMN_NAME = 'active'
);
SET @sql_roles_active = IF(
    @col_roles_active = 0, 
    'ALTER TABLE roles ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE', 
    'SELECT 1'
);
PREPARE stmt_roles FROM @sql_roles_active;
EXECUTE stmt_roles;
DEALLOCATE PREPARE stmt_roles;

-- 2. Ensure 'active' column exists on permissions table using MySQL 8 information_schema check & dynamic SQL
SET @col_perms_active = (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = @dbname 
      AND TABLE_NAME = 'permissions' 
      AND COLUMN_NAME = 'active'
);
SET @sql_perms_active = IF(
    @col_perms_active = 0, 
    'ALTER TABLE permissions ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE', 
    'SELECT 1'
);
PREPARE stmt_perms FROM @sql_perms_active;
EXECUTE stmt_perms;
DEALLOCATE PREPARE stmt_perms;

-- 3. Ensure all 9 canonical roles exist in roles table before remapping
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

-- 4. Safely migrate user_roles assignments to canonical roles avoiding duplicates
-- Group 1: ADMIN, SYSTEM_ADMIN -> SYSTEM_ADMINISTRATOR
DELETE ur_legacy FROM user_roles ur_legacy
JOIN roles legacy_r ON ur_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SYSTEM_ADMINISTRATOR'
JOIN user_roles ur_canon ON ur_canon.user_id = ur_legacy.user_id AND ur_canon.role_id = canon_r.id
WHERE legacy_r.name IN ('ADMIN', 'SYSTEM_ADMIN');

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SYSTEM_ADMINISTRATOR'
SET ur.role_id = canon_r.id
WHERE legacy_r.name IN ('ADMIN', 'SYSTEM_ADMIN');

-- Group 2: MANAGER, PROPERTY_MANAGER -> APARTMENT_MANAGER
DELETE ur_legacy FROM user_roles ur_legacy
JOIN roles legacy_r ON ur_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'APARTMENT_MANAGER'
JOIN user_roles ur_canon ON ur_canon.user_id = ur_legacy.user_id AND ur_canon.role_id = canon_r.id
WHERE legacy_r.name IN ('MANAGER', 'PROPERTY_MANAGER');

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'APARTMENT_MANAGER'
SET ur.role_id = canon_r.id
WHERE legacy_r.name IN ('MANAGER', 'PROPERTY_MANAGER');

-- Group 3: TENANT, RESIDENT -> TENANT_RESIDENT
DELETE ur_legacy FROM user_roles ur_legacy
JOIN roles legacy_r ON ur_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'TENANT_RESIDENT'
JOIN user_roles ur_canon ON ur_canon.user_id = ur_legacy.user_id AND ur_canon.role_id = canon_r.id
WHERE legacy_r.name IN ('TENANT', 'RESIDENT');

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'TENANT_RESIDENT'
SET ur.role_id = canon_r.id
WHERE legacy_r.name IN ('TENANT', 'RESIDENT');

-- Group 4: OWN -> OWNER
DELETE ur_legacy FROM user_roles ur_legacy
JOIN roles legacy_r ON ur_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'OWNER'
JOIN user_roles ur_canon ON ur_canon.user_id = ur_legacy.user_id AND ur_canon.role_id = canon_r.id
WHERE legacy_r.name = 'OWN';

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'OWNER'
SET ur.role_id = canon_r.id
WHERE legacy_r.name = 'OWN';

-- Group 5: SECURITY -> SECURITY_OFFICER
DELETE ur_legacy FROM user_roles ur_legacy
JOIN roles legacy_r ON ur_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SECURITY_OFFICER'
JOIN user_roles ur_canon ON ur_canon.user_id = ur_legacy.user_id AND ur_canon.role_id = canon_r.id
WHERE legacy_r.name = 'SECURITY';

UPDATE user_roles ur
JOIN roles legacy_r ON ur.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SECURITY_OFFICER'
SET ur.role_id = canon_r.id
WHERE legacy_r.name = 'SECURITY';

-- 5. Safely migrate role_permissions assignments to canonical roles avoiding duplicates
-- Group 1: ADMIN, SYSTEM_ADMIN -> SYSTEM_ADMINISTRATOR
DELETE rp_legacy FROM role_permissions rp_legacy
JOIN roles legacy_r ON rp_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SYSTEM_ADMINISTRATOR'
JOIN role_permissions rp_canon ON rp_canon.role_id = canon_r.id AND rp_canon.permission_id = rp_legacy.permission_id
WHERE legacy_r.name IN ('ADMIN', 'SYSTEM_ADMIN');

UPDATE role_permissions rp
JOIN roles legacy_r ON rp.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SYSTEM_ADMINISTRATOR'
SET rp.role_id = canon_r.id
WHERE legacy_r.name IN ('ADMIN', 'SYSTEM_ADMIN');

-- Group 2: MANAGER, PROPERTY_MANAGER -> APARTMENT_MANAGER
DELETE rp_legacy FROM role_permissions rp_legacy
JOIN roles legacy_r ON rp_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'APARTMENT_MANAGER'
JOIN role_permissions rp_canon ON rp_canon.role_id = canon_r.id AND rp_canon.permission_id = rp_legacy.permission_id
WHERE legacy_r.name IN ('MANAGER', 'PROPERTY_MANAGER');

UPDATE role_permissions rp
JOIN roles legacy_r ON rp.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'APARTMENT_MANAGER'
SET rp.role_id = canon_r.id
WHERE legacy_r.name IN ('MANAGER', 'PROPERTY_MANAGER');

-- Group 3: TENANT, RESIDENT -> TENANT_RESIDENT
DELETE rp_legacy FROM role_permissions rp_legacy
JOIN roles legacy_r ON rp_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'TENANT_RESIDENT'
JOIN role_permissions rp_canon ON rp_canon.role_id = canon_r.id AND rp_canon.permission_id = rp_legacy.permission_id
WHERE legacy_r.name IN ('TENANT', 'RESIDENT');

UPDATE role_permissions rp
JOIN roles legacy_r ON rp.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'TENANT_RESIDENT'
SET rp.role_id = canon_r.id
WHERE legacy_r.name IN ('TENANT', 'RESIDENT');

-- Group 4: OWN -> OWNER
DELETE rp_legacy FROM role_permissions rp_legacy
JOIN roles legacy_r ON rp_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'OWNER'
JOIN role_permissions rp_canon ON rp_canon.role_id = canon_r.id AND rp_canon.permission_id = rp_legacy.permission_id
WHERE legacy_r.name = 'OWN';

UPDATE role_permissions rp
JOIN roles legacy_r ON rp.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'OWNER'
SET rp.role_id = canon_r.id
WHERE legacy_r.name = 'OWN';

-- Group 5: SECURITY -> SECURITY_OFFICER
DELETE rp_legacy FROM role_permissions rp_legacy
JOIN roles legacy_r ON rp_legacy.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SECURITY_OFFICER'
JOIN role_permissions rp_canon ON rp_canon.role_id = canon_r.id AND rp_canon.permission_id = rp_legacy.permission_id
WHERE legacy_r.name = 'SECURITY';

UPDATE role_permissions rp
JOIN roles legacy_r ON rp.role_id = legacy_r.id
JOIN roles canon_r ON canon_r.name = 'SECURITY_OFFICER'
SET rp.role_id = canon_r.id
WHERE legacy_r.name = 'SECURITY';

-- 6. Delete foreign key references for any remaining non-canonical roles
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

-- 7. Delete legacy non-canonical roles
DELETE FROM roles WHERE name NOT IN (
    'SYSTEM_ADMINISTRATOR', 'APARTMENT_MANAGER', 'OWNER', 'TENANT_RESIDENT',
    'FINANCE_OFFICER', 'MAINTENANCE_COORDINATOR', 'TECHNICIAN', 'SERVICE_STAFF', 'SECURITY_OFFICER'
);

-- 8. Ensure active flag is TRUE for all 9 canonical roles
UPDATE roles SET active = TRUE WHERE name IN (
    'SYSTEM_ADMINISTRATOR', 'APARTMENT_MANAGER', 'OWNER', 'TENANT_RESIDENT',
    'FINANCE_OFFICER', 'MAINTENANCE_COORDINATOR', 'TECHNICIAN', 'SERVICE_STAFF', 'SECURITY_OFFICER'
);
