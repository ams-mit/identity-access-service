# Project A — Identity Access Service (`identity-access-service`)

**Group:** Group 1 — Apartment Identity and Resident Management  
**Package:** `kln.ams.identityaccess`  
**Base Path:** `/api/v1`  
**Database:** `identity_access_db`  
**Architecture:** Microservice architecture with Spring Boot 3.3.5 / Java 21 / REST / RS256 JWT Bearer Authentication  

---

## 1. Service Ownership & Boundaries

Per `PROJECT-A-CONTRACT-DECISIONS.md` and `IDENTITY-ACCESS-SERVICE.md`, `identity-access-service` is the canonical owner of:
- **User accounts** & credential management (bcrypt hashed, never plaintext/returned)
- **Account status** (`ACTIVE`, `INACTIVE`, `SUSPENDED`)
- **Canonical roles** (9 roles: `SYSTEM_ADMINISTRATOR`, `APARTMENT_MANAGER`, `OWNER`, `TENANT_RESIDENT`, `FINANCE_OFFICER`, `MAINTENANCE_COORDINATOR`, `TECHNICIAN`, `SERVICE_STAFF`, `SECURITY_OFFICER`)
- **Permissions** & role-permission mappings
- **User-role assignments**
- **User authentication** (`POST /api/v1/auth/login`)
- **User JWT issuance** (RS256 asymmetric signing, 30m TTL, strictly `sub`, `type=user`, `roles`, `iat`, `exp`)
- **Internal identity validation APIs** (`GET /api/v1/internal/users/{userId}/validate`, `GET /api/v1/internal/users/{userId}/status`)

### Explicit Non-Ownership Boundaries
The service does **not** own:
- Resident, owner, tenant, or staff domain profiles (owned by `resident-management-service`)
- Buildings, floors, units, unit types, or unit ownership (owned by `property-unit-service`)
- Leases, occupants, or occupancy (owned by `lease-occupancy-service`)
- Invoices, payments, or balances (owned by `billing-payment-service`)
- Maintenance requests or work orders (owned by `operations-service`)
- Notifications (solely owned by `community-service`)

---

## 2. API Contract Inventory (19 Canonical Endpoints)

### Authentication & Account Identity
| API ID | Method | Endpoint | Description | Access |
|---|---|---|---|---|
| `AUTH-001` | `POST` | `/api/v1/auth/login` | Authenticate user and issue User JWT | Public |
| `AUTH-002` | `GET` | `/api/v1/auth/me` | Retrieve authenticated user account details | Authenticated User |

### User Management
| API ID | Method | Endpoint | Description | Access |
|---|---|---|---|---|
| `USR-001` | `GET` | `/api/v1/users` | List user accounts (paginated, filtered, search) | `SYSTEM_ADMINISTRATOR` |
| `USR-002` | `POST` | `/api/v1/users` | Create user account with canonical roles | `SYSTEM_ADMINISTRATOR` |
| `USR-003` | `GET` | `/api/v1/users/{userId}` | Get user account by UUID | `SYSTEM_ADMINISTRATOR` |
| `USR-004` | `PATCH` | `/api/v1/users/{userId}` | Update account fields (email, names, phone) | `SYSTEM_ADMINISTRATOR` |
| `USR-005` | `PATCH` | `/api/v1/users/{userId}/status` | Update account status (`ACTIVE`, `INACTIVE`, `SUSPENDED`) | `SYSTEM_ADMINISTRATOR` |
| `USR-006` | `GET` | `/api/v1/users/{userId}/roles` | Get canonical roles assigned to user | `SYSTEM_ADMINISTRATOR` |
| `USR-007` | `PUT` | `/api/v1/users/{userId}/roles` | Replace canonical roles assigned to user | `SYSTEM_ADMINISTRATOR` |

### Role Management
| API ID | Method | Endpoint | Description | Access |
|---|---|---|---|---|
| `ROLE-001` | `GET` | `/api/v1/roles` | List all canonical system roles | `SYSTEM_ADMINISTRATOR` |
| `ROLE-002` | `POST` | `/api/v1/roles` | Create new role definition | `SYSTEM_ADMINISTRATOR` |
| `ROLE-003` | `GET` | `/api/v1/roles/{roleId}` | Get role details by UUID | `SYSTEM_ADMINISTRATOR` |
| `ROLE-004` | `PATCH` | `/api/v1/roles/{roleId}` | Update role description | `SYSTEM_ADMINISTRATOR` |
| `ROLE-005` | `DELETE` | `/api/v1/roles/{roleId}` | Delete role (rejected if assigned to users) | `SYSTEM_ADMINISTRATOR` |

### Permission Management
| API ID | Method | Endpoint | Description | Access |
|---|---|---|---|---|
| `PERM-001` | `GET` | `/api/v1/permissions` | List all defined system permissions | `SYSTEM_ADMINISTRATOR` |
| `PERM-002` | `GET` | `/api/v1/roles/{roleId}/permissions` | Get permissions assigned to a role | `SYSTEM_ADMINISTRATOR` |
| `PERM-003` | `PUT` | `/api/v1/roles/{roleId}/permissions` | Replace permissions assigned to a role | `SYSTEM_ADMINISTRATOR` |

### Internal Microservice Validation APIs
| API ID | Method | Endpoint | Description | Access |
|---|---|---|---|---|
| `IAM-INT-001` | `GET` | `/api/v1/internal/users/{userId}/validate` | Validate user existence, active status, roles | Registered Service JWT |
| `IAM-INT-002` | `GET` | `/api/v1/internal/users/{userId}/status` | Validate current user account status | Registered Service JWT |

---

## 3. Standard Response Envelopes & Error Handling

All responses follow the canonical Project A API response standard:

### Success Response
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "7f83a9b2-4df2-4d8e-9c7f-4d7e5e7a4c11"
}
```

### Error Response
```json
{
  "success": false,
  "message": "Human-readable error message",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": null
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "7f83a9b2-4df2-4d8e-9c7f-4d7e5e7a4c11"
}
```

---

## 4. Local Development & Setup

### Prerequisites
- Java 21 JDK
- Maven Wrapper (`./mvnw` or `.\mvnw.cmd`)
- MySQL 8.0 or Docker

### 1. Environment Configuration
Copy `.env.example` to `.env` and verify settings:
```env
DB_URL=jdbc:mysql://localhost:3306/identity_access_db
DB_USERNAME=identity_user
DB_PASSWORD=identity_pass
SERVER_PORT=8080
JWT_PRIVATE_KEY_PATH=file:certs/private_key.pem
JWT_PUBLIC_KEY_PATH=file:certs/public_key.pem
GATEWAY_JWT_PUBLIC_KEY=file:certs/gateway_public_key.pem
SERVICE_JWT_PRIVATE_KEY=file:certs/service_private_key.pem
```

### 2. Generate Development Keypair
```bash
.\mvnw.cmd compile exec:java -Dexec.mainClass="kln.ams.identityaccess.security.RsaKeyPairGenerator"
```

### 3. Run Application
```bash
.\mvnw.cmd spring-boot:run
```

### 4. Run Tests
```bash
.\mvnw.cmd test
```

### 5. API Documentation
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI Specification: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`
