INSERT INTO roles (id, name, description) VALUES
  (UUID_TO_BIN(UUID()), 'SYSTEM_ADMIN', 'Full administrative access'),
  (UUID_TO_BIN(UUID()), 'MANAGER', 'Apartment Manager'),
  (UUID_TO_BIN(UUID()), 'OWNER', 'Property Owner'),
  (UUID_TO_BIN(UUID()), 'TENANT', 'Tenant'),
  (UUID_TO_BIN(UUID()), 'RESIDENT', 'Resident'),
  (UUID_TO_BIN(UUID()), 'FINANCE_OFFICER', 'Finance Officer'),
  (UUID_TO_BIN(UUID()), 'TECHNICIAN', 'Maintenance Technician'),
  (UUID_TO_BIN(UUID()), 'SECURITY', 'Security Staff');
