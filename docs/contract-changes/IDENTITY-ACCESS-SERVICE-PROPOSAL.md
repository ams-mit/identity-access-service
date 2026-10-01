# Project A — Identity Access Service

**File:** `10-IDENTITY-ACCESS-SERVICE.md`  
**Service:** `identity-access-service`  
**Service ID:** `IDENTITY-ACCESS`  
**Group:** Group 1 — Apartment Identity and Resident Management  
**Repository:** `ams-mit/identity-access-service`  
**Package:** `kln.ams.identityaccess`  
**API version:** `v1`  
**Base path:** `/api/v1`

---

## 1. Purpose

This document is the **canonical target-state contract** for `identity-access-service` in Project A.

The service is responsible for:

- user authentication;
- user accounts;
- account status;
- roles;
- permissions;
- role assignment;
- issuing User JWTs;
- identity validation for other Project A services.

Group 1's documented scope includes the `identity-access-service` and `resident-management-service`, with frontend areas for `/auth`, `/profile`, `/admin/users`, `/admin/roles`, `/residents`, `/owners`, `/tenants`, and `/staff`. The Group 1 minimum user stories include role-based user-account management, secure login, permitted-function access, profile management, and documented validation for other services.

The apartment case identifies `User`, `Role`, `Permission`, `UserRole`, and `AccountStatus` as identity-domain entities.

**Source basis:** Group 1 responsibilities and the Apartment Management System case scope. The exact endpoint set and some detailed field/authorization decisions below are the Project A canonical contract derived from those requirements and the project's shared API/JWT standards.

---

# 2. Source-of-Truth Rule

The existing repository is **not** the source of truth.

This document defines the target state.

When implementing or synchronizing the repository, the AI/developer agent must:

1. inspect the complete existing repository;
2. inspect controllers, services, repositories, entities, DTOs, security configuration, migrations, tests, OpenAPI configuration, and configuration files;
3. compare the implementation with this contract;
4. remove obsolete APIs;
5. remove duplicate APIs;
6. remove conflicting APIs;
7. remove obsolete business logic;
8. modify incompatible implementation;
9. create missing functionality;
10. update database schema/migrations;
11. update security and authorization;
12. update tests;
13. update OpenAPI;
14. compile;
15. run automated tests;
16. verify authentication and authorization;
17. verify service-to-service communication;
18. verify database behavior;
19. verify standard error responses;
20. verify Swagger/OpenAPI;
21. verify health checks;
22. remove temporary/obsolete artifacts;
23. leave the repository synchronized with this contract.

Do **not** preserve an old endpoint merely because it currently exists.

Do **not** add compatibility aliases unless they are explicitly added to the canonical contract.

---

# 3. Responsibility Boundary

## 3.1 Identity Access owns

| Area | Ownership |
|---|---|
| User account | `identity-access-service` |
| Authentication credentials | `identity-access-service` |
| Account status | `identity-access-service` |
| Role definitions | `identity-access-service` |
| Permission definitions | `identity-access-service` |
| User-role assignments | `identity-access-service` |
| Authentication | `identity-access-service` |
| User JWT issuance | `identity-access-service` |
| Identity validation | `identity-access-service` |

## 3.2 Identity Access does not own

The following belong to other services:

| Data/function | Owning service |
|---|---|
| Resident profile | `resident-management-service` |
| Owner profile | `resident-management-service` |
| Tenant/resident relationship | `resident-management-service` |
| Staff profile | `resident-management-service` |
| Building | `property-unit-service` |
| Floor | `property-unit-service` |
| Unit | `property-unit-service` |
| Ownership of a unit | `property-unit-service` |
| Lease | `lease-occupancy-service` |
| Occupancy | `lease-occupancy-service` |
| Billing/invoice/payment | `billing-payment-service` |
| Utility charge | `utility-charge-service` |
| Maintenance/work order | `operations-service` |
| Facility/booking | `operations-service` |
| Visitor | `community-service` |
| Announcement | `community-service` |
| Notification | `community-service` |
| Apartment/unit ownership or occupancy | Relevant property/occupancy service |

Identity Access must not create or maintain another service's domain records.

---

# 4. Technology and Repository Contract

## 4.1 Technology

- Java 21
- Spring Boot 4.1.1
- Maven
- Spring Web
- Spring Validation
- Spring Security
- JWT implementation supporting RS256
- OpenAPI/Swagger
- MySQL
- Automated tests
- Docker

## 4.2 Maven group/package

```text
groupId:
kln.ams

base package:
kln.ams.identityaccess
```

Recommended package structure:

```text
kln.ams.identityaccess
├── config
├── controller
├── dto
│   ├── auth
│   ├── user
│   ├── role
│   ├── permission
│   └── internal
├── entity
├── exception
├── repository
├── security
├── service
└── util
```

The exact internal class decomposition may differ, but the external API contract must not.

---

# 5. Database

Database name:

```text
identity_access_db
```

Database ownership is exclusive to this service.

No other service may directly query or modify this database.

Other services must use the documented APIs.

## 5.1 Core data model

The implementation should support the following identity concepts:

```text
User
Role
Permission
UserRole
RolePermission
AccountStatus
```

A physical implementation may represent status as an enum or reference table, and may model many-to-many relationships through join tables.

## 5.2 User account data

The identity account may contain fields such as:

```text
id
username
email
password_hash
first_name
last_name
phone
status
created_at
updated_at
last_login_at
```

These are identity/account fields only.

Resident, owner, tenant, and staff domain profiles must remain in `resident-management-service`.

## 5.3 Password storage

Passwords must:

- never be stored in plaintext;
- never be returned by an API;
- never be logged;
- be stored only as a secure password hash;
- be validated through the configured password hashing mechanism.

The exact hashing implementation must follow the project's security requirements and must not expose password material.

---

# 6. Canonical Roles

The Project A canonical role vocabulary is:

```text
SYSTEM_ADMINISTRATOR
APARTMENT_MANAGER
OWNER
TENANT_RESIDENT
FINANCE_OFFICER
MAINTENANCE_COORDINATOR
TECHNICIAN
SERVICE_STAFF
SECURITY_OFFICER
```

The apartment case documents the corresponding project responsibilities as System Administrator, Apartment Manager, Owner, Tenant/Resident, Finance Officer, Maintenance Coordinator, Technician/Service Staff, and Security Officer.

The split between `TECHNICIAN` and `SERVICE_STAFF` is a **Project A contract decision** so that the documented "Technician / Service Staff" responsibility can be represented as distinct authorization roles.

## 6.1 Role naming rule

New APIs must use only the canonical names above.

Legacy values such as:

```text
ADMIN
SYSTEM_ADMIN
MANAGER
PROPERTY_MANAGER
TENANT
RESIDENT
OWN
```

must not be introduced into new API contracts.

If legacy values exist in the repository, they must be migrated/normalized or removed as part of synchronization with this contract.

---

# 7. Role Responsibility Model

The identity service stores the role assignments. Domain services remain responsible for domain-specific authorization.

| Role | Identity-level purpose |
|---|---|
| `SYSTEM_ADMINISTRATOR` | Manage system accounts, roles, permissions, and access settings |
| `APARTMENT_MANAGER` | Authenticated management role; identity data may be used by downstream domain authorization |
| `OWNER` | Authenticated owner identity |
| `TENANT_RESIDENT` | Authenticated resident/tenant identity |
| `FINANCE_OFFICER` | Authenticated finance identity |
| `MAINTENANCE_COORDINATOR` | Authenticated maintenance coordination identity |
| `TECHNICIAN` | Authenticated technician identity |
| `SERVICE_STAFF` | Authenticated service-staff identity |
| `SECURITY_OFFICER` | Authenticated security identity |

A role in the JWT does **not** prove:

- ownership of a unit;
- tenancy;
- occupancy;
- access to a specific apartment;
- eligibility for a specific facility;
- responsibility for a particular work order;
- any other domain relationship.

The owning domain service must perform those checks.

---

# 8. Authentication Architecture

The project uses:

```text
Frontend
    ↓
API Gateway
    ↓
identity-access-service
```

for user authentication.

For normal protected requests:

```text
Frontend
    ↓ User JWT
API Gateway
    ↓ validates User JWT
    ↓ creates Gateway JWT
Backend Service
```

For internal calls:

```text
Service A
    ↓ Service JWT
API Gateway
    ↓ validates Service JWT
    ↓ creates Gateway JWT
Service B
```

Identity Access does **not** directly trust another backend service's JWT for ordinary user authentication.

The Gateway is the central trust point for incoming User JWTs and Service JWTs.

---

# 9. JWT Contract

Identity Access owns the **User JWT signing key pair**.

```text
Identity Access Private Key
Identity Access Public Key
```

The private key remains only in Identity Access.

The public key is provided to the API Gateway.

## 9.1 Algorithm

```text
RS256
```

No symmetric shared JWT secret is used for the canonical Project A architecture.

## 9.2 User JWT claims

The canonical User JWT contains only:

```json
{
  "sub": "user-uuid",
  "type": "user",
  "roles": [
    "TENANT_RESIDENT"
  ],
  "iat": 1750000000,
  "exp": 1750001800
}
```

Required claims:

| Claim | Meaning |
|---|---|
| `sub` | authenticated user UUID |
| `type` | must be `user` |
| `roles` | canonical role names |
| `iat` | issued-at Unix timestamp |
| `exp` | expiry Unix timestamp |

Do not add:

```text
password
passwordHash
permissions
email
phone
residentProfile
unitId
ownership
occupancy
lease
billing data
profile data
secret
kid
iss
aud
```

unless the global security standard is explicitly changed.

## 9.3 Token lifetime

Recommended Project A lifetime:

```text
User access JWT: 30 minutes
```

The value may be configuration-driven, but the service must use a bounded expiry.

---

# 10. JWT Issuance Rules

On successful login:

1. validate credentials;
2. verify account is active;
3. load the user's canonical roles;
4. create a User JWT;
5. sign it using the Identity Access private key;
6. return the access token;
7. never expose the private key.

The token must not be issued for an inactive/deactivated account.

## 10.1 Gateway interaction

The frontend receives the User JWT.

The frontend sends:

```http
Authorization: Bearer <USER_JWT>
```

The Gateway:

1. validates RS256;
2. verifies the signature using the Identity Access public key;
3. checks `type=user`;
4. checks required claims;
5. checks expiration;
6. applies Gateway-level rules;
7. creates a new Gateway JWT;
8. forwards the Gateway JWT downstream.

The original User JWT must not be forwarded to backend services as the downstream trust token.

---

# 11. Authentication and Authorization Rules

## Authentication

Authentication answers:

```text
Who is the caller?
```

Identity Access provides user authentication.

## Authorization

Authorization answers:

```text
Can the caller perform this operation?
```

Authorization requires:

1. valid authenticated identity;
2. active account;
3. required canonical role;
4. required domain relationship/scope where applicable.

Identity Access provides the identity and role information.

Domain services enforce domain-specific authorization.

---

# 12. Public API Inventory

The canonical public endpoint set is:

| API ID | Method | Endpoint | Purpose |
|---|---|---|---|
| `AUTH-001` | POST | `/api/v1/auth/login` | Authenticate user and issue User JWT |
| `AUTH-002` | GET | `/api/v1/auth/me` | Return authenticated account identity |
| `AUTH-003` | POST | `/api/v1/auth/logout` | Logout authenticated user |
| `AUTH-004` | POST | `/api/v1/auth/register` | Self-register new user account |
| `AUTH-005` | POST | `/api/v1/auth/forgot-password` | Request password reset instructions |
| `AUTH-006` | POST | `/api/v1/auth/reset-password` | Reset password using reset token |
| `AUTH-007` | PUT | `/api/v1/auth/me/password` | Change own password |
| `USR-001` | GET | `/api/v1/users` | List user accounts |
| `USR-002` | POST | `/api/v1/users` | Create a user account |
| `USR-003` | GET | `/api/v1/users/{userId}` | Get a user account |
| `USR-004` | PATCH | `/api/v1/users/{userId}` | Update account fields |
| `USR-005` | PATCH | `/api/v1/users/{userId}/status` | Activate/deactivate account |
| `USR-006` | GET | `/api/v1/users/{userId}/roles` | Get assigned roles |
| `USR-007` | PUT | `/api/v1/users/{userId}/roles` | Replace assigned roles |
| `ROLE-001` | GET | `/api/v1/roles` | List roles |
| `ROLE-002` | POST | `/api/v1/roles` | Create a role |
| `ROLE-003` | GET | `/api/v1/roles/{roleId}` | Get a role |
| `ROLE-004` | PATCH | `/api/v1/roles/{roleId}` | Update a role |
| `ROLE-005` | DELETE | `/api/v1/roles/{roleId}` | Remove a role definition where permitted |
| `PERM-001` | GET | `/api/v1/permissions` | List permission definitions |
| `PERM-002` | GET | `/api/v1/roles/{roleId}/permissions` | Get permissions assigned to a role |
| `PERM-003` | PUT | `/api/v1/roles/{roleId}/permissions` | Replace permissions assigned to a role |
| `IAM-INT-001` | GET | `/api/v1/internal/users/{userId}/validate` | Validate user existence, account status, and roles |
| `IAM-INT-002` | GET | `/api/v1/internal/users/{userId}/status` | Validate current account status |

**Total canonical provider endpoints: 24.**

The exact public endpoint set above is a Project A contract decision based on the documented identity scope. It is not claimed that every endpoint is explicitly listed verbatim in the assignment brief.

---

# 13. Endpoint Specifications

## 13.1 AUTH-001 — Login

### Request

```http
POST /api/v1/auth/login
Content-Type: application/json
Accept: application/json
X-Request-ID: <UUID>
```

Request body:

```json
{
  "username": "john@example.com",
  "password": "Password123!"
}
```

### Authentication

No JWT required.

### Required role

None.

### Purpose

Authenticate a user and issue a User JWT.

### Validation

- `username` required;
- `password` required;
- credentials must match;
- account must exist;
- account must be active;
- disabled/deactivated account cannot receive a User JWT.

### Success

```http
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<USER_JWT>",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "user": {
      "id": "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11",
      "username": "john@example.com",
      "roles": [
        "TENANT_RESIDENT"
      ],
      "status": "ACTIVE"
    }
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "..."
}
```

### Errors

| Status | Code |
|---|---|
| `400` | `VALIDATION_ERROR` |
| `401` | `INVALID_CREDENTIALS` |
| `403` | `ACCOUNT_INACTIVE` |
| `429` | `RATE_LIMIT_EXCEEDED` |
| `500` | `INTERNAL_SERVER_ERROR` |

The response must not reveal whether a username exists when doing so would expose account information unnecessarily.

---

## 13.2 AUTH-002 — Current Authenticated Account

### Request

```http
GET /api/v1/auth/me
Authorization: Bearer <USER_JWT>
```

### Authentication

Required.

### Roles

Any authenticated canonical role.

### Purpose

Return the authenticated identity/account information used by the frontend profile/authentication state.

### Success

```http
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Authenticated account retrieved successfully",
  "data": {
    "id": "uuid",
    "username": "john@example.com",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Perera",
    "roles": [
      "TENANT_RESIDENT"
    ],
    "status": "ACTIVE"
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "..."
}
```

Do not return password hashes, private security material, or another service's domain data.

### Errors

```text
401 INVALID_TOKEN
403 ACCOUNT_INACTIVE
404 USER_NOT_FOUND
```

---

## 13.3 AUTH-003 — Logout

### Request

```http
POST /api/v1/auth/logout
Authorization: Bearer <USER_JWT>
X-Request-ID: <UUID>
```

### Authentication

Required. User JWT.

### Required role

Any authenticated canonical role.

### Purpose

Logs out the authenticated user. In accordance with the stateless JWT architecture, the server performs structured audit logging (`LOGOUT`) while the client discards its stored token. No token revocation table is maintained.

### Success

```http
204 No Content
```

No response body.

### Errors

| Status | Code |
|---|---|
| `401` | `INVALID_TOKEN` |

---

## 13.4 AUTH-004 — Register

### Request

```http
POST /api/v1/auth/register
Content-Type: application/json
Accept: application/json
X-Request-ID: <UUID>
```

Request body:

```json
{
  "email": "resident@example.com",
  "password": "StrongPassword123!",
  "confirmPassword": "StrongPassword123!",
  "firstName": "Jane",
  "lastName": "Doe",
  "phoneNumber": "+1234567890"
}
```

### Authentication

No JWT required (Public).

### Required role

None.

### Purpose

Self-registration for new users. Creates an account with username matching email (trimmed, lowercased), assigns canonical role `TENANT_RESIDENT`, and sets account status to `INACTIVE`. Requires administrator activation via `USR-005` before login is allowed. Client role input is strictly forbidden and ignored.

### Validation

- `email` required, valid email format;
- `password` required, satisfies centralized password policy (min 8 chars, uppercase, lowercase, digit, special character);
- `confirmPassword` required, must match `password`;
- `firstName` and `lastName` optional / sanitized;
- `email` / `username` must not already be registered.

### Success

```http
201 Created
```

Example:

```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "id": "c1f7a230-6d4b-4b11-9a72-88ec0c5a2101",
    "username": "resident@example.com",
    "email": "resident@example.com",
    "firstName": "Jane",
    "lastName": "Doe",
    "status": "INACTIVE",
    "roles": [
      "TENANT_RESIDENT"
    ],
    "createdAt": "2026-10-01T08:00:00Z"
  },
  "timestamp": "2026-10-01T08:00:00Z",
  "requestId": "..."
}
```

Password and password hashes are never returned.

### Errors

| Status | Code |
|---|---|
| `400` | `VALIDATION_ERROR` |
| `409` | `USER_ALREADY_EXISTS` |
| `500` | `INTERNAL_SERVER_ERROR` |

---

## 13.5 AUTH-005 — Forgot Password

### Request

```http
POST /api/v1/auth/forgot-password
Content-Type: application/json
Accept: application/json
X-Request-ID: <UUID>
```

Request body:

```json
{
  "email": "resident@example.com"
}
```

### Authentication

No JWT required (Public).

### Required role

None.

### Purpose

Initiate password reset flow. Generates a 32-byte cryptographically secure random token (URL-safe Base64) with a configurable expiration (default 15 minutes). Only the SHA-256 hash of the token is stored in `password_reset_tokens`. Any previous unused tokens for the user are invalidated. To protect against user enumeration, an identical generic 200 response is unconditionally returned regardless of whether the email exists or the account is active.

### Validation

- `email` required, valid email format.

### Success

```http
200 OK
```

Example:

```json
{
  "success": true,
  "message": "If the email is registered and active, password reset instructions have been sent.",
  "data": {
    "message": "If the email is registered and active, password reset instructions have been sent."
  },
  "timestamp": "2026-10-01T08:00:00Z",
  "requestId": "..."
}
```

### Errors

| Status | Code |
|---|---|
| `400` | `VALIDATION_ERROR` |
| `500` | `INTERNAL_SERVER_ERROR` |

---

## 13.6 AUTH-006 — Reset Password

### Request

```http
POST /api/v1/auth/reset-password
Content-Type: application/json
Accept: application/json
X-Request-ID: <UUID>
```

Request body:

```json
{
  "token": "4vK9eZ3u_9pL...",
  "newPassword": "NewStrongPassword123!",
  "confirmPassword": "NewStrongPassword123!"
}
```

### Authentication

No JWT required (Public).

### Required role

None.

### Purpose

Completes password reset using the token provided via out-of-band communication (e.g., email). Validates the SHA-256 hash of the raw token, checks expiry, verifies the token has not been used, and verifies the user is `ACTIVE`. Updates the user's password using BCrypt, marks the token as used, and invalidates all other tokens for that user.

### Validation

- `token` required;
- `newPassword` required, satisfies centralized password policy;
- `confirmPassword` required, must match `newPassword`;
- Token must exist, be unused, not expired, and belong to an `ACTIVE` account.

### Success

```http
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Password has been reset successfully.",
  "data": {
    "message": "Password has been reset successfully."
  },
  "timestamp": "2026-10-01T08:00:00Z",
  "requestId": "..."
}
```

### Errors

| Status | Code |
|---|---|
| `400` | `INVALID_RESET_TOKEN` |
| `400` | `VALIDATION_ERROR` |
| `500` | `INTERNAL_SERVER_ERROR` |

Note: If the token is missing, expired, already used, or the account is inactive, the identical generic error message `"Invalid or expired password reset token"` is returned with code `INVALID_RESET_TOKEN`.

---

## 13.7 AUTH-007 — Change Password

### Request

```http
PUT /api/v1/auth/me/password
Authorization: Bearer <USER_JWT>
Content-Type: application/json
Accept: application/json
X-Request-ID: <UUID>
```

Request body:

```json
{
  "currentPassword": "OldPassword123!",
  "newPassword": "NewPassword123!",
  "confirmPassword": "NewPassword123!"
}
```

### Authentication

Required. User JWT.

### Required role

Any authenticated canonical role.

### Purpose

Allows an authenticated user to change their own password. Verifies the current password against the stored BCrypt hash, checks that `newPassword` satisfies password complexity rules, ensures `confirmPassword` matches, and rejects changes where `newPassword` is identical to `currentPassword`.

### Validation

- `currentPassword` required;
- `newPassword` required, satisfies centralized password policy;
- `confirmPassword` required, must match `newPassword`;
- `newPassword` must differ from `currentPassword`;
- `currentPassword` must match the stored password.

### Success

```http
204 No Content
```

No response body.

### Errors

| Status | Code |
|---|---|
| `400` | `INVALID_CREDENTIALS` |
| `400` | `VALIDATION_ERROR` |
| `401` | `INVALID_TOKEN` |
| `500` | `INTERNAL_SERVER_ERROR` |

---

# 14. User Account APIs

## 14.1 USR-001 — List Users

```http
GET /api/v1/users
```

### Authentication

Required.

### Roles

```text
SYSTEM_ADMINISTRATOR
```

### Query parameters

```text
page
size
status
role
search
```

Recommended pagination:

```text
page=0
size=20
```

Maximum recommended page size:

```text
100
```

### Purpose

List identity accounts for authorized account administration.

### Response

```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "username": "john@example.com",
      "email": "john@example.com",
      "firstName": "John",
      "lastName": "Perera",
      "phone": "0771234567",
      "status": "ACTIVE",
      "roles": [
        "TENANT_RESIDENT"
      ],
      "createdAt": "2026-09-30T12:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "7f83a9b2-..."
}
```

---

## 14.2 USR-002 — Create User

```http
POST /api/v1/users
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Request

```json
{
  "username": "john@example.com",
  "email": "john@example.com",
  "password": "Password123!",
  "firstName": "John",
  "lastName": "Perera",
  "phone": "0771234567",
  "roles": [
    "TENANT_RESIDENT"
  ]
}
```

### Validation

- username required and unique;
- email required and valid;
- password required and must satisfy configured password policy;
- first name required;
- last name required;
- roles must contain only canonical roles;
- requested roles must exist;
- account must start in a valid account status.

### Success

```http
201 Created
```

The response must never contain the submitted password or password hash.

### Errors

```text
400 VALIDATION_ERROR
401 INVALID_TOKEN
403 PERMISSION_DENIED
409 USER_ALREADY_EXISTS
422 INVALID_ROLE
```

---

## 14.3 USR-003 — Get User

```http
GET /api/v1/users/{userId}
```

### Authentication

Required.

### Roles

```text
SYSTEM_ADMINISTRATOR
```

### Purpose

Retrieve an identity account.

### Errors

```text
401 INVALID_TOKEN
403 PERMISSION_DENIED
404 USER_NOT_FOUND
```

---

## 14.4 USR-004 — Update User

```http
PATCH /api/v1/users/{userId}
```

### Authentication

Required.

### Roles

```text
SYSTEM_ADMINISTRATOR
```

### Request

Example:

```json
{
  "email": "new.email@example.com",
  "firstName": "John",
  "lastName": "Perera",
  "phone": "0771234567"
}
```

### Rules

- account ID cannot be changed;
- username changes, if supported by implementation, must preserve uniqueness;
- role changes must use the dedicated role endpoint;
- status changes must use the dedicated status endpoint;
- password changes must not be performed through an unrestricted user update endpoint.

### Success

```http
200 OK
```

### Errors

```text
400 VALIDATION_ERROR
403 PERMISSION_DENIED
404 USER_NOT_FOUND
409 USERNAME_OR_EMAIL_ALREADY_EXISTS
```

---

## 14.5 USR-005 — Account Status

```http
PATCH /api/v1/users/{userId}/status
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Request

```json
{
  "status": "ACTIVE",
  "reason": "Administrative activation upon identity verification"
}
```

Canonical status values:

```text
ACTIVE
INACTIVE
SUSPENDED
```

### Rules

- only valid status transitions are allowed;
- deactivated/suspended users must not authenticate successfully;
- status changes must be auditable;
- status changes must not delete historical identity data.

### Success

```http
200 OK
```

### Errors

```text
400 VALIDATION_ERROR
403 PERMISSION_DENIED
404 USER_NOT_FOUND
409 INVALID_STATUS_TRANSITION
```

---

## 14.6 USR-006 — Get User Roles

```http
GET /api/v1/users/{userId}/roles
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Success

```json
{
  "success": true,
  "message": "User roles retrieved successfully",
  "data": {
    "userId": "uuid",
    "roles": [
      "TENANT_RESIDENT"
    ]
  },
  "timestamp": "...",
  "requestId": "..."
}
```

---

## 14.7 USR-007 — Replace User Roles

```http
PUT /api/v1/users/{userId}/roles
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Request

```json
{
  "roles": [
    "TENANT_RESIDENT"
  ]
}
```

### Validation

- role names must be canonical;
- every role must exist;
- duplicate roles are invalid or normalized before persistence;
- protected system account rules must be enforced;
- role changes must be auditable.

### Success

```http
200 OK
```

### Errors

```text
400 VALIDATION_ERROR
403 PERMISSION_DENIED
404 USER_NOT_FOUND
422 INVALID_ROLE
```

---

# 14. Role APIs

## 15.1 ROLE-001 — List Roles

```http
GET /api/v1/roles
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Success

```json
{
  "success": true,
  "message": "Roles retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "name": "TENANT_RESIDENT",
      "description": "Authenticated tenant/resident role",
      "active": true
    }
  ],
  "timestamp": "...",
  "requestId": "..."
}
```

The canonical project roles must be represented consistently.

---

## 15.2 ROLE-002 — Create Role

```http
POST /api/v1/roles
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Request

```json
{
  "name": "TENANT_RESIDENT",
  "description": "Authenticated tenant/resident role"
}
```

### Rules

The implementation must not create aliases for canonical roles.

A role name must be unique.

If the project decides that the canonical nine roles are fixed, creation must reject attempts to create duplicate canonical roles.

### Success

```http
201 Created
```

### Errors

```text
400 VALIDATION_ERROR
403 PERMISSION_DENIED
409 ROLE_ALREADY_EXISTS
```

---

## 15.3 ROLE-003 — Get Role

```http
GET /api/v1/roles/{roleId}
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Success

```http
200 OK
```

### Errors

```text
403 PERMISSION_DENIED
404 ROLE_NOT_FOUND
```

---

## 15.4 ROLE-004 — Update Role

```http
PATCH /api/v1/roles/{roleId}
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Request

```json
{
  "description": "Updated role description"
}
```

Role-name changes must not silently change canonical role identifiers used by JWTs or dependent services.

### Success

```http
200 OK
```

### Errors

```text
400 VALIDATION_ERROR
403 PERMISSION_DENIED
404 ROLE_NOT_FOUND
409 ROLE_CONFLICT
```

---

## 15.5 ROLE-005 — Delete Role

```http
DELETE /api/v1/roles/{roleId}
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Rules

A role must not be deleted while it is required by active user assignments unless those assignments are explicitly migrated.

For canonical project roles, deactivation is preferred where historical/audit integrity matters.

### Success

```http
204 No Content
```

### Errors

```text
403 PERMISSION_DENIED
404 ROLE_NOT_FOUND
409 ROLE_IN_USE
```

---

# 15. Permission APIs

The project requires role-based access and identifies `Permission` as an identity-domain entity.

Permissions are stored by Identity Access but are **not included in the User JWT**.

The JWT carries canonical roles only.

---

## 16.1 PERM-001 — List Permissions

```http
GET /api/v1/permissions
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Success

```json
{
  "success": true,
  "message": "Permissions retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "code": "USER_MANAGE",
      "description": "Manage identity user accounts",
      "active": true
    }
  ],
  "timestamp": "...",
  "requestId": "..."
}
```

Permission codes must be stable identifiers.

---

## 16.2 PERM-002 — Get Role Permissions

```http
GET /api/v1/roles/{roleId}/permissions
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Success

```json
{
  "success": true,
  "message": "Role permissions retrieved successfully",
  "data": {
    "roleId": "uuid",
    "roleName": "SYSTEM_ADMINISTRATOR",
    "permissions": [
      {
        "id": "uuid",
        "code": "USER_MANAGE",
        "description": "Manage identity user accounts",
        "active": true
      }
    ]
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "7f83a9b2-..."
}
```

---

## 16.3 PERM-003 — Replace Role Permissions

```http
PUT /api/v1/roles/{roleId}/permissions
```

### Authentication

Required.

### Role

```text
SYSTEM_ADMINISTRATOR
```

### Request

```json
{
  "permissionIds": [
    "uuid",
    "uuid"
  ]
}
```

### Rules

- every permission must exist;
- duplicate permission IDs must not be persisted (normalized by backend);
- assignment must be auditable;
- permissions must not be copied into User JWTs;
- dependent services must continue to authorize from the canonical role contract.

### Success

```http
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Role permissions replaced successfully",
  "data": {
    "roleId": "uuid",
    "roleName": "SYSTEM_ADMINISTRATOR",
    "permissions": [
      {
        "id": "uuid",
        "code": "USER_MANAGE",
        "description": "Manage identity user accounts",
        "active": true
      }
    ]
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "7f83a9b2-..."
}
```

### Errors

```text
400 VALIDATION_ERROR
403 PERMISSION_DENIED
404 ROLE_NOT_FOUND
422 PERMISSION_NOT_FOUND
```

---

# 16. Internal API Contract

Internal APIs are used by other Project A services.

All internal endpoints use:

```text
/api/v1/internal/...
```

They are not public frontend APIs.

Internal calls must use the project's Service JWT → Gateway → Gateway JWT architecture.

No service may directly query `identity_access_db`.

---

# 17. IAM-INT-001 — Validate User

```http
GET /api/v1/internal/users/{userId}/validate
```

### Authentication

Required.

### Allowed callers

Registered Project A backend services that have a documented dependency on Identity Access.

Expected callers include:

```text
resident-management-service
property-unit-service
lease-occupancy-service
billing-payment-service
utility-charge-service
operations-service
community-service
```

Only callers that actually require identity validation should invoke this endpoint.

### Purpose

Validate that a user identity exists and provide the minimum identity/account information required for the calling service.

### Query parameters

Optional:

```text
requiredRole
```

If supplied, the service validates whether the user has the specified canonical role.

### Success

```http
200 OK
```

Example:

```json
{
  "success": true,
  "message": "User validation successful",
  "data": {
    "userId": "uuid",
    "exists": true,
    "active": true,
    "roles": [
      "TENANT_RESIDENT"
    ],
    "roleMatches": true
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "7f83a9b2-..."
}
```

### Important boundary

This response must not include:

```text
unit ownership
unit occupancy
lease
resident profile
billing
maintenance
visitor
facility
notification
```

Those are owned by other services.

### Errors

```text
400 VALIDATION_ERROR
401 INVALID_SERVICE_TOKEN
403 CALLER_SERVICE_NOT_ALLOWED
404 USER_NOT_FOUND
503 DEPENDENCY_UNAVAILABLE
```

---

# 18. IAM-INT-002 — User Account Status

```http
GET /api/v1/internal/users/{userId}/status
```

### Authentication

Required.

### Allowed callers

Registered backend services with a documented need to validate account status.

### Purpose

Provide the current account status for cross-service authorization/validation.

### Success

```json
{
  "success": true,
  "message": "User account status retrieved successfully",
  "data": {
    "userId": "uuid",
    "status": "ACTIVE",
    "active": true
  },
  "timestamp": "...",
  "requestId": "..."
}
```

### Errors

```text
401 INVALID_SERVICE_TOKEN
403 CALLER_SERVICE_NOT_ALLOWED
404 USER_NOT_FOUND
```

---

# 20. Internal Caller Authorization

The Identity Access service must authorize internal callers based on the authenticated calling service.

A valid Service JWT alone is not sufficient.

The endpoint must verify that the calling service is registered and allowed to invoke the specific endpoint.

For example:

```text
Service JWT
    ↓
Gateway verifies service identity
    ↓
Gateway creates Gateway JWT
    ↓
Identity Access verifies Gateway JWT
    ↓
Identity Access checks endpoint caller policy
```

Do not use an arbitrary user role as the authorization mechanism for service-to-service calls.

---

# 21. Public API Authentication Matrix

| API | Auth | Required role |
|---|---|---|
| `AUTH-001` Login | None | None |
| `AUTH-002` Current account | User JWT | Any authenticated role |
| `AUTH-003` Logout | User JWT | Any authenticated role |
| `AUTH-004` Register | None | None |
| `AUTH-005` Forgot password | None | None |
| `AUTH-006` Reset password | None | None |
| `AUTH-007` Change password | User JWT | Any authenticated role |
| `USR-001` List users | User JWT | `SYSTEM_ADMINISTRATOR` |
| `USR-002` Create user | User JWT | `SYSTEM_ADMINISTRATOR` |
| `USR-003` Get user | User JWT | `SYSTEM_ADMINISTRATOR` |
| `USR-004` Update user | User JWT | `SYSTEM_ADMINISTRATOR` |
| `USR-005` Change status | User JWT | `SYSTEM_ADMINISTRATOR` |
| `USR-006` Get roles | User JWT | `SYSTEM_ADMINISTRATOR` |
| `USR-007` Replace roles | User JWT | `SYSTEM_ADMINISTRATOR` |
| `ROLE-001` List roles | User JWT | `SYSTEM_ADMINISTRATOR` |
| `ROLE-002` Create role | User JWT | `SYSTEM_ADMINISTRATOR` |
| `ROLE-003` Get role | User JWT | `SYSTEM_ADMINISTRATOR` |
| `ROLE-004` Update role | User JWT | `SYSTEM_ADMINISTRATOR` |
| `ROLE-005` Delete role | User JWT | `SYSTEM_ADMINISTRATOR` |
| `PERM-001` List permissions | User JWT | `SYSTEM_ADMINISTRATOR` |
| `PERM-002` Role permissions | User JWT | `SYSTEM_ADMINISTRATOR` |
| `PERM-003` Replace permissions | User JWT | `SYSTEM_ADMINISTRATOR` |
| `IAM-INT-001` Validate user | Service JWT | Allowed calling service |
| `IAM-INT-002` User status | Service JWT | Allowed calling service |

---

# 22. Relationship Boundary

Identity Access must not decide whether a user is an owner, tenant, resident, or staff member based only on a role.

Example:

```text
JWT:
roles = ["TENANT_RESIDENT"]
```

does not prove:

```text
user → occupies → unit
```

The appropriate resident/property/occupancy service must validate the relationship.

For example, a maintenance request for a unit should follow the documented cross-service validation flow rather than trusting a JWT claim containing a unit ID.

---

# 23. Cross-Service Dependencies

Identity Access is a foundational service.

Other services may depend on it for:

- user validation;
- account status;
- role validation;
- authentication context.

Identity Access must not depend on another domain service merely to issue a login token.

## 23.1 Dependency direction

```text
                    ┌──────────────────────────┐
                    │ identity-access-service  │
                    └────────────┬─────────────┘
                                 │
                  identity/role  │ validation
                                 ▼
     ┌────────────┬─────────────┬──────────────┬─────────────┐
     ▼            ▼             ▼              ▼             ▼
  Resident      Property       Lease         Billing       Operations
  Community     Utility        ...
```

Domain-specific relationships remain with their owning services.

---

# 24. Dependency Failure Behavior

Identity Access must not fabricate successful validation results.

If Identity Access itself depends on an unavailable local infrastructure dependency such as its database, a protected operation must fail safely.

For a downstream service calling Identity Access, an unavailable Identity Access dependency must be represented using the standard error:

```http
503 Service Unavailable
```

Example:

```json
{
  "success": false,
  "message": "Identity service is temporarily unavailable",
  "error": {
    "code": "DEPENDENCY_UNAVAILABLE",
    "details": {
      "service": "identity-access-service"
    }
  },
  "timestamp": "...",
  "requestId": "..."
}
```

Do not return:

```json
{
  "success": true,
  "data": {
    "exists": true
  }
}
```

when validation could not actually be performed.

---

# 25. Request/Response Standard

All endpoints use the global Project A API response envelope.

## Success

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "uuid"
}
```

## Error

```json
{
  "success": false,
  "message": "Unable to process request",
  "error": {
    "code": "ERROR_CODE",
    "details": null
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "uuid"
}
```

## Validation error

```json
{
  "success": false,
  "message": "Request validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": [
      {
        "field": "email",
        "message": "must be a valid email address"
      },
      {
        "field": "password",
        "message": "must satisfy the password policy"
      }
    ]
  },
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "uuid"
}
```

---

# 26. Common Error Codes

Identity Access may use these canonical error codes:

| Code | Meaning |
|---|---|
| `VALIDATION_ERROR` | Request validation failed |
| `INVALID_CREDENTIALS` | Login credentials are invalid |
| `INVALID_TOKEN` | JWT is missing, malformed, invalid, or expired |
| `INVALID_RESET_TOKEN` | Password reset token is missing, expired, already used, or user is inactive |
| `ACCOUNT_INACTIVE` | Account cannot authenticate because it is inactive |
| `PERMISSION_DENIED` | Authenticated caller lacks required role |
| `CALLER_SERVICE_NOT_ALLOWED` | Calling service is not authorized |
| `USER_NOT_FOUND` | User does not exist |
| `USER_ALREADY_EXISTS` | User already exists |
| `USERNAME_OR_EMAIL_ALREADY_EXISTS` | Unique identity field already exists |
| `ROLE_NOT_FOUND` | Role does not exist |
| `ROLE_ALREADY_EXISTS` | Role already exists |
| `ROLE_IN_USE` | Role cannot be deleted while referenced |
| `INVALID_ROLE` | Role is not a canonical/registered role |
| `PERMISSION_NOT_FOUND` | Permission does not exist |
| `INVALID_STATUS_TRANSITION` | Account status transition is invalid |
| `RATE_LIMIT_EXCEEDED` | Request rate exceeded |
| `DEPENDENCY_UNAVAILABLE` | Required dependency unavailable |
| `INTERNAL_SERVER_ERROR` | Unexpected server failure |

---

# 27. Validation Rules

All request DTOs must use server-side validation.

Examples:

```java
@NotBlank
private String username;

@NotBlank
@Email
private String email;

@NotBlank
private String password;
```

Additional rules:

- UUID parameters must be valid UUIDs;
- enum/status values must be canonical;
- collection values must not contain invalid duplicates;
- pagination values must be bounded;
- email must satisfy configured format validation;
- username must satisfy configured uniqueness and format rules;
- password must satisfy the configured password policy;
- role names must use the canonical vocabulary;
- permission IDs must reference existing permissions;
- request bodies must reject unknown or unsafe fields according to the project's DTO policy.

---

# 28. Account Status Rules

Canonical account statuses:

```text
ACTIVE
INACTIVE
SUSPENDED
```

Minimum authentication rule:

```text
ACTIVE → login allowed
INACTIVE → login rejected
SUSPENDED → login rejected
```

A status change does not delete the account.

Historical account information should remain auditable.

---

# 29. Role Assignment Rules

Role assignments must:

- use canonical role names;
- reference existing roles;
- be stored in Identity Access;
- be auditable;
- not be duplicated;
- be reflected in newly issued JWTs.

Role changes do not retroactively rewrite already-issued JWTs.

The bounded token lifetime limits the duration of stale role claims. Account status must still be checked according to the service/security implementation where immediate access revocation is required.

---

# 30. Password Rules

The exact password policy must be centralized in configuration and documented in the deployment configuration.

At minimum:

- password must never be stored plaintext;
- password must never appear in logs;
- password must never be returned in API responses;
- password must not be placed in JWT claims;
- password reset/change functionality must not expose the password;
- authentication failure responses must not reveal sensitive credential details.

---

# 31. Security Requirements

Identity Access is security-critical.

It must:

- use RS256;
- protect the RSA private key;
- expose only the public key where required;
- validate JWT claims before trusting them;
- reject unsupported algorithms;
- validate token expiry;
- validate token type;
- validate role values;
- use HTTPS in deployed environments;
- never log credentials;
- never log private keys;
- never log JWT secrets;
- never expose password hashes;
- validate request bodies;
- return standard 401/403 responses;
- avoid stack traces in client responses;
- protect administrative endpoints with authorization.

---

# 32. JWT Key Configuration

Private keys must be supplied through secure configuration.

Example environment variables:

```text
IDENTITY_JWT_PRIVATE_KEY
IDENTITY_JWT_PUBLIC_KEY
IDENTITY_JWT_EXPIRATION_SECONDS=1800
```

The exact configuration representation may use mounted secret files instead of environment variables.

`.env.example` may document variable names, but must never contain real secrets.

Do not commit:

```text
private keys
real secrets
real passwords
production credentials
JWT signing secrets
```

---

# 33. Public Key Distribution

The Identity Access public key is trusted by the API Gateway.

Trust relationship:

```text
Identity Access Public Key
          ↓
     API Gateway
```

Backend services do not need to directly trust the Identity Access private key or another service's private key.

The Gateway validates the User JWT and then issues a Gateway JWT.

---

# 34. Gateway Integration

The Gateway must route:

```text
/api/v1/auth/**
/api/v1/users/**
/api/v1/roles/**
/api/v1/permissions/**
/api/v1/internal/users/**
```

to Identity Access according to the project's Gateway routing configuration.

The Gateway is responsible for common ingress concerns.

Identity Access remains responsible for:

- user authentication;
- user account management;
- role management;
- permission management;
- User JWT issuance;
- identity validation.

The Gateway must not implement Identity Access business logic.

---

# 35. API Gateway Request Flow — Login

```text
Frontend
   |
   | POST /api/v1/auth/login
   | username + password
   v
API Gateway
   |
   | route
   v
Identity Access
   |
   | validate credentials
   | create User JWT
   v
Identity Access
   |
   | User JWT
   v
API Gateway
   |
   | response
   v
Frontend
```

The login credential payload must not be logged by the Gateway.

---

# 36. API Gateway Request Flow — Protected User Request

```text
Frontend
   |
   | Authorization: Bearer <USER_JWT>
   v
API Gateway
   |
   | verify Identity Access signature
   | verify type=user
   | verify exp
   | verify required claims
   |
   | create Gateway JWT
   v
Target Backend Service
```

The original User JWT is not the downstream trust token.

---

# 37. Internal Service Flow

Example:

```text
billing-payment-service
        |
        | Service JWT
        v
API Gateway
        |
        | verifies service JWT
        | creates Gateway JWT
        v
identity-access-service
        |
        | validate user
        v
billing-payment-service
```

Identity Access must authorize the calling service.

A user role is not a replacement for service identity.

---

# 38. Logging

Important operations should produce structured logs.

Examples:

```text
LOGIN_SUCCESS
LOGIN_FAILURE
USER_CREATED
USER_UPDATED
USER_STATUS_CHANGED
USER_ROLES_CHANGED
ROLE_CREATED
ROLE_UPDATED
ROLE_DELETED
ROLE_PERMISSIONS_CHANGED
USER_VALIDATION_REQUEST
```

Recommended log context:

```text
timestamp
service
requestId
userId
operation
result
errorCode
```

Do not log:

```text
password
passwordHash
privateKey
JWT secret
full Authorization header
sensitive credentials
```

For failed login, avoid logging the raw password.

---

# 39. Auditability

Administrative identity operations should be traceable.

At minimum, changes to:

- user creation;
- account status;
- role assignments;
- role definitions;
- role permissions;

should have sufficient audit/log context to determine:

```text
who performed the operation
what operation occurred
which account/role was affected
when it occurred
whether it succeeded
requestId
```

The exact persistent audit model is an implementation decision unless separately added to the project-wide contract.

---

# 40. Health

The service must expose:

```http
GET /actuator/health
```

The health check should verify the service is running and, where configured, that its database dependency is available.

Example:

```json
{
  "status": "UP"
}
```

The health endpoint must not expose secrets, passwords, JWT keys, or database credentials.

---

# 41. OpenAPI

Identity Access must publish OpenAPI documentation.

Expected endpoints:

```text
/swagger-ui.html
/v3/api-docs
```

Every API must document:

- API ID;
- HTTP method;
- endpoint;
- purpose;
- authentication;
- required roles;
- allowed internal callers;
- path parameters;
- query parameters;
- request body;
- validation;
- success response;
- error responses;
- business error codes;
- examples;
- security requirements.

The OpenAPI specification must match this contract.

Do not leave undocumented controllers in the repository.

---

# 42. Testing Requirements

## 42.1 Unit tests

Test at least:

- password validation;
- login success;
- invalid credentials;
- inactive account login rejection;
- JWT creation;
- JWT required claims;
- role assignment;
- invalid role rejection;
- user creation validation;
- duplicate username/email;
- status transitions;
- internal user validation;
- internal caller authorization;
- permission assignment;
- business exception handling.

## 42.2 Controller/API tests

Test:

```text
200
201
204
400
401
403
404
409
422
429
500
```

where applicable to the endpoint.

## 42.3 Security tests

Test:

- missing Authorization header;
- malformed Bearer token;
- wrong JWT algorithm;
- invalid signature;
- expired JWT;
- wrong token type;
- invalid role;
- unauthorized administrative operation;
- unauthorized internal service caller.

## 42.4 Integration tests

Verify:

```text
Frontend/Gateway → Login → Identity
Gateway → Identity protected endpoint
Service → Gateway → Identity internal endpoint
Identity → MySQL
```

No direct cross-service database access is permitted.

---

# 43. Postman Requirements

A Postman collection must cover:

```text
Login (AUTH-001)
Current account (AUTH-002)
Logout (AUTH-003)
Register (AUTH-004)
Forgot password (AUTH-005)
Reset password (AUTH-006)
Change password (AUTH-007)
Create user (USR-002)
Get user
Update user
Change account status
Get user roles
Replace user roles
List roles
Create role
Get role
Update role
Delete role
List permissions
Get role permissions
Replace role permissions
Internal user validation
Internal user status
```

The collection must demonstrate authentication and representative 401/403/validation/error cases.

---

# 44. Database Migration Requirements

Database schema changes must be versioned.

Do not manually modify the shared database during normal development without updating the migration/source-of-truth schema process.

Migrations must cover:

- users;
- roles;
- permissions;
- user-role relationships;
- role-permission relationships;
- required indexes and uniqueness constraints;
- account status;
- timestamps;
- required audit fields.

Database migrations must not create tables belonging to another service.

---

# 45. API Contract Change Rules

Before changing an endpoint:

1. identify dependent services;
2. update this service contract;
3. update the cross-service API registry;
4. notify dependent teams;
5. update OpenAPI;
6. update Postman;
7. update automated tests;
8. update Gateway routing if necessary;
9. perform integration testing.

Do not silently change:

- endpoint path;
- method;
- authentication requirement;
- role requirement;
- request field;
- response field;
- field type;
- error code;
- status code;
- internal caller rules.

Breaking changes require a new major API version such as:

```text
/api/v2/...
```

when the project integration process determines that versioning is required.

---

# 46. Forbidden Patterns

The implementation must not:

- store plaintext passwords;
- log passwords;
- expose password hashes;
- put permissions in User JWTs;
- put apartment/unit relationships in JWTs;
- trust an unverified JWT;
- accept non-RS256 tokens;
- use arbitrary legacy role names in new APIs;
- directly access another service's database;
- own resident profiles;
- own unit/lease/occupancy records;
- own billing/payment records;
- own maintenance/facility records;
- own visitors/announcements/notifications;
- duplicate another service's API;
- expose undocumented controllers;
- return incompatible error envelopes;
- bypass the API Gateway for normal inter-service communication;
- use a user JWT as a Service JWT;
- use a Service JWT as a User JWT;
- fabricate successful identity validation when the database is unavailable;
- silently preserve obsolete endpoints only for backward compatibility.

---

# 47. Existing-Code Cleanup Rules

When synchronizing an existing repository:

### Remove

- duplicate authentication endpoints;
- duplicate user endpoints;
- obsolete role aliases;
- obsolete JWT implementations;
- legacy JWT algorithms;
- endpoints outside this service's ownership;
- direct cross-service database access;
- old response formats;
- controller-specific error formats;
- undocumented internal endpoints;
- obsolete DTOs/entities that only support removed APIs;
- obsolete migrations when safe and consistent with the repository's migration strategy;
- temporary test/debug endpoints;
- hard-coded secrets.

### Modify

- incompatible routes;
- incompatible role names;
- incompatible JWT claims;
- authentication filters;
- authorization rules;
- database schema;
- DTOs;
- service logic;
- tests;
- OpenAPI;
- Gateway integration configuration.

### Create

- missing canonical endpoints;
- missing DTOs;
- missing validation;
- missing role/permission handling;
- missing internal validation APIs;
- missing security configuration;
- missing tests;
- missing OpenAPI documentation;
- missing migrations;
- missing health integration.

---

# 48. Final Implementation Checklist

## Repository

- [ ] Repository is `identity-access-service`
- [ ] Package is `kln.ams.identityaccess`
- [ ] Java 21
- [ ] Spring Boot 4.1.1
- [ ] Maven
- [ ] MySQL configured
- [ ] `identity_access_db` used
- [ ] No direct access to another service database

## Identity

- [ ] User account model exists
- [ ] Role model exists
- [ ] Permission model exists
- [ ] User-role relationship exists
- [ ] Role-permission relationship exists
- [ ] Account status exists
- [ ] Canonical role names are used
- [ ] Legacy role aliases are removed/normalized

## Authentication

- [ ] Login endpoint exists
- [ ] Passwords are securely hashed
- [ ] Passwords are never returned
- [ ] User JWT uses RS256
- [ ] User JWT has `sub`
- [ ] User JWT has `type=user`
- [ ] User JWT has `roles`
- [ ] User JWT has `iat`
- [ ] User JWT has `exp`
- [ ] User JWT lifetime is bounded
- [ ] Inactive accounts cannot log in
- [ ] Private key is protected
- [ ] Public key is available to Gateway
- [ ] JWT secrets are not committed

## Authorization

- [ ] Administrative endpoints require authorization
- [ ] `SYSTEM_ADMINISTRATOR` is used for IAM administration
- [ ] Role names are validated
- [ ] Permission assignments are protected
- [ ] Internal service callers are authenticated
- [ ] Internal caller authorization is enforced
- [ ] Domain relationships are not inferred from JWT alone

## APIs

- [ ] `AUTH-001`
- [ ] `AUTH-002`
- [ ] `AUTH-003`
- [ ] `AUTH-004`
- [ ] `AUTH-005`
- [ ] `AUTH-006`
- [ ] `AUTH-007`
- [ ] `USR-001`
- [ ] `USR-002`
- [ ] `USR-003`
- [ ] `USR-004`
- [ ] `USR-005`
- [ ] `USR-006`
- [ ] `USR-007`
- [ ] `ROLE-001`
- [ ] `ROLE-002`
- [ ] `ROLE-003`
- [ ] `ROLE-004`
- [ ] `ROLE-005`
- [ ] `PERM-001`
- [ ] `PERM-002`
- [ ] `PERM-003`
- [ ] `IAM-INT-001`
- [ ] `IAM-INT-002`

## API standards

- [ ] `/api/v1`
- [ ] REST naming
- [ ] JSON camelCase
- [ ] UUID identifiers
- [ ] ISO-8601 timestamps
- [ ] Standard success envelope
- [ ] Standard error envelope
- [ ] Standard error codes
- [ ] `X-Request-ID` propagation
- [ ] OpenAPI complete
- [ ] Swagger available
- [ ] Global exception handling implemented

## Testing

- [ ] Unit tests
- [ ] Controller tests
- [ ] Security tests
- [ ] Integration tests
- [ ] Postman collection
- [ ] Negative/error cases
- [ ] JWT tests
- [ ] Role authorization tests
- [ ] Internal caller tests

## Operations

- [ ] `/actuator/health`
- [ ] Structured logging
- [ ] No secrets in logs
- [ ] Docker build works
- [ ] Environment configuration documented
- [ ] Database migrations work
- [ ] Clean repository state

---

# 49. Canonical Identity Access Boundary

The final boundary is:

```text
                    ┌─────────────────────────────┐
                    │     identity-access-service │
                    │                             │
                    │ User                        │
                    │ Role                        │
                    │ Permission                  │
                    │ UserRole                    │
                    │ RolePermission              │
                    │ AccountStatus               │
                    │ Authentication              │
                    │ User JWT issuance           │
                    │ Identity validation         │
                    └──────────────┬──────────────┘
                                   │
                     documented APIs
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
          ▼                        ▼                        ▼
 resident-management       property/occupancy          billing/utility
 operations/community      other Project A services
```

Identity Access provides **identity and access information**.

It does not become the owner of apartment-domain relationships.

---

# 50. Canonical Security Flow

```text
USER AUTHENTICATION

Frontend
   │
   │ username + password
   ▼
API Gateway
   │
   │ route
   ▼
Identity Access
   │
   │ validate credentials
   │ sign User JWT with Identity Private Key
   ▼
Frontend
   │
   │ User JWT
   ▼
API Gateway
   │
   │ verify Identity Public Key
   │ verify type=user
   │ verify claims
   │ create Gateway JWT
   ▼
Backend Service
   │
   │ verify Gateway Public Key
   │ authorize role/domain scope
   ▼
Business Operation
```

```text
INTERNAL SERVICE AUTHENTICATION

Service A
   │
   │ Service JWT
   ▼
API Gateway
   │
   │ verify Service A public key
   │ create Gateway JWT
   ▼
Identity Access
   │
   │ verify Gateway JWT
   │ authorize Service A
   ▼
Identity Validation
```

---

# 51. Final Contract Statement

`identity-access-service` is the Project A owner of **authentication, user accounts, roles, permissions, account status, user-role assignments, and User JWT issuance**.

It is a foundational service for the other Project A services.

It must expose stable, documented APIs, maintain its own database, use the project's standard JWT/Gateway architecture, and provide identity validation without taking ownership of resident, apartment, occupancy, financial, operational, or community domain data.

The canonical implementation target is this document plus:

```text
00-PROJECT-A-CONTRACT-DECISIONS.md
01-PROJECT-A-GLOBAL-API-STANDARD.md
02-PROJECT-A-JWT-SECURITY-STANDARD.md
03-PROJECT-A-CROSS-SERVICE-API-REGISTRY.md
```

Where an existing repository conflicts with this target contract, the repository must be changed to conform to the canonical contract rather than the contract being weakened to preserve obsolete implementation.

---

# 52. Contract Alignment Summary & Open Items

This section records the alignment decisions implemented in `identity-access-service` according to the priority order:
1. `PROJECT-A-CONTRACT-DECISIONS.md`
2. `PROJECT-A-GLOBAL-API-STANDARD.md`
3. `PROJECT-A-JWT-SECURITY-STANDARD.md`
4. `IDENTITY-ACCESS-SERVICE.md`
5. `PROJECT-A-CROSS-SERVICE-API-REGISTRY.md`

### 52.1 Restored and Canonical Endpoints (AUTH-003 .. AUTH-007)
The full suite of 24 provider endpoints is confirmed canonical and active:
- `AUTH-003`: `POST /api/v1/auth/logout` (204 No Content, stateless token discard)
- `AUTH-004`: `POST /api/v1/auth/register` (201 Created, default self-registration)
- `AUTH-005`: `POST /api/v1/auth/forgot-password` (200 OK, generic timing-safe message)
- `AUTH-006`: `POST /api/v1/auth/reset-password` (200 OK, reset password with cryptographic token)
- `AUTH-007`: `PUT /api/v1/auth/me/password` (204 No Content, authenticated password change)

### 52.2 Canonical Error Code: INVALID_RESET_TOKEN
In `AUTH-006` (`/api/v1/auth/reset-password`), when a token is missing, expired, already consumed, or linked to an inactive account, the service responds with HTTP 400 and error code `INVALID_RESET_TOKEN` and message `"Invalid or expired password reset token"`.

### 52.3 Additive Contract Fields
The following additive fields are included in the implementation:
- **`roleMatches`** (`Boolean`): Added to `IAM-INT-001` (`/api/v1/internal/users/{userId}/validate`). Populated when `requiredRole` query parameter is provided.
- **`reason`** (`String`): Added to `USR-005` (`PATCH /api/v1/users/{userId}/status`) request body for audit trail tracking.
- **`roleName`** (`String`) & **`active`** (`Boolean`): Added to `PERM-002` and `PERM-003` responses (`RolePermissionsResponse` and `PermissionSummaryResponse`).
- **`phone`** (`String`) & **`createdAt`** (`Instant`): Retained in `UserResponse` across user management endpoints (`USR-001` .. `USR-004`).

### 52.4 USR-001 List Format (Global API Standard)
Per `PROJECT-A-GLOBAL-API-STANDARD.md`, collection list endpoints use top-level `data` array and top-level `pagination` object:
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": [ ... ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-10-01T08:00:00Z",
  "requestId": "..."
}
```
Validation limits: default `page=0`, `size=20`, maximum `size=100`. Sizes exceeding 100 return HTTP 400 `VALIDATION_ERROR`.

### 52.5 Validation Details Format (Global API Standard)
Per `PROJECT-A-GLOBAL-API-STANDARD.md`, validation errors are formatted as an array of field errors:
```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": [
      {
        "field": "permissionIds",
        "message": "Permission IDs cannot be empty"
      }
    ]
  },
  "timestamp": "2026-10-01T08:00:00Z",
  "requestId": "..."
}
```

### 52.6 Internal API Identifiers
Internal user validation APIs are formally registered as:
- `IAM-INT-001`: `GET /api/v1/internal/users/{userId}/validate`
- `IAM-INT-002`: `GET /api/v1/internal/users/{userId}/status`

### 52.7 Open Item for Team Lead Confirmation: Spring Boot Version
- **Contract Specification**: Stated as Spring Boot `4.1.1` in Section 4.1 of `10-IDENTITY-ACCESS-SERVICE.md`.
- **Repository Implementation**: Spring Boot `3.3.5` on Java 21 (since Spring Boot 4.x is not a released version; Spring Framework is at 6.x and Spring Boot is at 3.x).
- **Status**: Documented as an open question for Team Lead confirmation in `docs/contract-changes/TEAM-LEAD-DECISIONS.md`. No code change to Spring Boot version per safety rules.

