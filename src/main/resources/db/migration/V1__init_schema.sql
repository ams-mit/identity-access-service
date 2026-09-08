CREATE TABLE roles (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,       -- SYSTEM_ADMIN, MANAGER, OWNER, TENANT, STAFF
    description VARCHAR(255)
);

CREATE TABLE permissions (
    id BINARY(16) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,      -- e.g. RESIDENT_MANAGE, OWNER_MANAGE
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id),
    FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE TABLE users (
    id BINARY(16) PRIMARY KEY,
    username VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | INACTIVE
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE user_unit_relationships (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    unit_id BINARY(16) NOT NULL,             -- owned by Group 2, referenced not joined
    relationship_type VARCHAR(20) NOT NULL,  -- OWNER | TENANT | RESIDENT
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE account_status_audit (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    changed_by BINARY(16) NOT NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
