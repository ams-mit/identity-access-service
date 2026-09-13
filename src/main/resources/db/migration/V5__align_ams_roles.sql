-- V5__align_ams_roles.sql
-- Align seed roles with official AMS role names (Sprint 2 IAM-08)

UPDATE roles SET name = 'SYSTEM_ADMINISTRATOR', description = 'Full administrative access' WHERE name = 'SYSTEM_ADMIN';
UPDATE roles SET name = 'APARTMENT_MANAGER', description = 'Apartment Manager' WHERE name = 'MANAGER';
UPDATE roles SET name = 'TENANT_RESIDENT', description = 'Tenant or Resident' WHERE name = 'TENANT';
DELETE FROM roles WHERE name = 'RESIDENT';
UPDATE roles SET name = 'SECURITY_OFFICER', description = 'Security Staff' WHERE name = 'SECURITY';

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'MAINTENANCE_COORDINATOR', 'Maintenance Coordinator'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'MAINTENANCE_COORDINATOR');

-- Ensure all 8 roles exist even on fresh installs where names might differ
INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'SYSTEM_ADMINISTRATOR', 'Full administrative access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SYSTEM_ADMINISTRATOR');

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'APARTMENT_MANAGER', 'Apartment Manager'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'APARTMENT_MANAGER');

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'OWNER', 'Property Owner'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'OWNER');

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'TENANT_RESIDENT', 'Tenant or Resident'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TENANT_RESIDENT');

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'FINANCE_OFFICER', 'Finance Officer'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'FINANCE_OFFICER');

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'TECHNICIAN', 'Maintenance Technician'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TECHNICIAN');

INSERT INTO roles (id, name, description)
SELECT UUID_TO_BIN(UUID()), 'SECURITY_OFFICER', 'Security Staff'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SECURITY_OFFICER');
