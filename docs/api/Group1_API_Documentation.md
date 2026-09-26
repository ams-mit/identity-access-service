# Group 1 — API Documentation
## Apartment Management System (Project A)

**Domain:** Identity, Access, Residents & User Relationships
**Owned Microservices:** `identity-access-service`, `resident-management-service`
**Source documents:** Group 1 SRS Draft, Group 1 UI Requirements & Screen Specification, Project A JWT Authentication & Security Standard
**Status:** Approved & Conformed to Implementation (`identity-access-service` on `develop` branch). Path/versioning convention (`/api/v1/...` and `/internal/v1/...`) and unified response envelope `{ "data": ..., "meta": ... }` confirmed and implemented.

---

## 0. Conventions Used in This Document

| Item | Convention |
|---|---|
| Base path (external, via API Gateway) | `/api/v1/...` |
| Base path (internal, service-to-service, direct/via Gateway) | `/internal/v1/...` |
| Auth header | `Authorization: Bearer <JWT>` |
| Token types accepted | `Gateway User JWT` (`type=user`) or `Gateway Service JWT` (`type=service`) — per JWT Standard §7 |
| Success envelope | Unified envelope `{ "data": <payload>, "meta": <metadata-or-null> }`. Paginated responses include `meta: { "page": 0, "size": 20, "totalElements": 100 }`. Non-paginated responses omit `meta` or provide `"meta": null`. |
| Error envelope | `{ "error": { "code": "STRING_CODE", "message": "safe human-readable message", "details": [...] } }` |
| Pagination | `?page=0&size=20` query params; response `meta.page`, `meta.size`, `meta.totalElements` |
| IDs | UUID strings unless stated otherwise |

**Standard HTTP status codes used throughout:** `200 OK`, `201 Created`, `204 No Content`, `400 Bad Request` (validation / domain rules), `401 Unauthorized` (missing/invalid JWT or wrong token type), `403 Forbidden` (authenticated but not permitted / unauthorized caller service), `404 Not Found`, `409 Conflict` (duplicate/state conflict), `423 Locked` (account lockout), `429 Too Many Requests` (rate limited), `500 Internal Server Error`.

Every endpoint below traces back to an FR/NFR code in the Group 1 SRS and a screen (UI-xxx) in the UI spec, so acceptance testing can be traced end to end: **Requirement → Backlog Story → API → Screen → Test.**

---

# PART A — `identity-access-service`

Owns: `User`, `Role`, `Permission`, `UserRole`, `AccountStatus`, authentication, JWT issuance, identity audit trail.

## A1. Authentication & Registration

### Story: US-G1-01 — Self-Registration
*As an unregistered user, I want to create a basic account so that I can access the system with restricted access until verified.*
**Traces to:** FR-IAM-001–006, UC-IAM-001, UI-002

| | |
|---|---|
| **Endpoint** | `POST /api/v1/auth/register` |
| **Auth** | None (public) |
| **Request** | `{ "firstName", "lastName", "email", "phone", "password", "confirmPassword", "requestedRole" }` |
| **Business rules** | Email must be unique (409 on duplicate). Password must meet policy (min 8 chars, at least 1 digit). `fullName` is not accepted in request payload; it is derived server-side from `firstName` and `lastName`. `requestedRole` is an advisory field restricted to resident-facing roles (`OWNER` or `TENANT_RESIDENT`) for administrative review; no privileged staff or system role accepted (FR-IAM-004). Server sets `accountStatus = PENDING_VERIFICATION` (FR-IAM-006). System Administrator accounts cannot be created via this endpoint (FR-IAM-002). |
| **Response 201** | `{ "data": { "userId", "email", "accountStatus": "PENDING_VERIFICATION", "requestedRole": "OWNER" } }` |
| **Errors** | `400` invalid/missing fields, password mismatch, invalid requestedRole; `409` duplicate email |

### Story: US-G1-02 — Login
*As a registered user, I want to log in with email and password so that I receive a JWT for authenticated access.*
**Traces to:** FR-IAM-011–018, NFR-SEC-001, UC-IAM-002, UI-001

| | |
|---|---|
| **Endpoint** | `POST /api/v1/auth/login` |
| **Auth** | None (public) |
| **Request** | `{ "email", "password" }` |
| **Business rules** | Verify account status is `ACTIVE`-eligible before issuing token (FR-IAM-012); `SUSPENDED`/`DEACTIVATED` → `403` (FR-IAM-007). 5 consecutive failed attempts → 15-minute lockout (`423`), reset on success (FR-IAM-016–018). Generic error message; never reveal whether the email exists (FR-IAM-017). |
| **Response 200** | `{ "data": { "accessToken": "<User JWT, type=user, RS256>", "expiresIn": 1800, "user": { "userId", "email", "roles": [...] } } }` |
| **Errors** | `401` invalid credentials (generic message); `403` account suspended/deactivated/pending-restricted; `423` locked out |

### Story: US-G1-03 — Logout
*As an authenticated user, I want to log out so that my session ends.*
**Traces to:** FR-IAM-015

| | |
|---|---|
| **Endpoint** | `POST /api/v1/auth/logout` |
| **Auth** | User JWT |
| **Response 204** | — (client discards token; server-side token invalidation if a revocation list is implemented) |

### Story: US-G1-04 — Change Password
*As an authenticated user, I want to change my password so that I can keep my account secure.*
**Traces to:** FR-IAM-019, FR-IAM-021–023, UI-008

| | |
|---|---|
| **Endpoint** | `PUT /api/v1/users/me/password` |
| **Auth** | User JWT (self only) |
| **Request** | `{ "currentPassword", "newPassword", "confirmPassword" }` |
| **Business rules** | Verify current password. Hash new password (never store/log plaintext, FR-IAM-021–022). Invalidate prior sessions/tokens where supported (FR-IAM-023). Raise `FR-AUD-007` audit event. |
| **Response 204** | — |
| **Errors** | `400` policy/mismatch; `401` wrong current password |

### Story: US-G1-05 — Forgot Password
*As a user who forgot their password, I want to start a reset process without revealing account existence.*
**Traces to:** FR-IAM-020, UC-IAM-003, UI-003

| | |
|---|---|
| **Endpoint** | `POST /api/v1/auth/forgot-password` |
| **Auth** | None (public) |
| **Request** | `{ "email" }` |
| **Response 200** | `{ "data": { "message": "If an account is associated with this email, instructions will be provided." } }` (always this response, regardless of match — FR-IAM-017 principle applied) |

### Story: US-G1-06 — Reset Password
*As a user, I want to set a new password after completing reset verification.*
**Traces to:** FR-IAM-020, FR-IAM-023, UC-IAM-003, UI-004

| | |
|---|---|
| **Endpoint** | `POST /api/v1/auth/reset-password` |
| **Auth** | None — authorizes via reset token issued in the forgot-password flow |
| **Request** | `{ "token", "newPassword", "confirmPassword" }` |
| **Business rules** | Invalidate reset token after use; invalidate prior sessions (FR-IAM-023). Anti-enumeration: invalid, expired, and already-used tokens all return an identical generic 400 error. Password must meet complexity policy and match confirmation. |
| **Response 200** | `{ "data": { "message": "Password has been reset successfully. You may now log in with your new password." } }` |
| **Errors** | `400` invalid/expired token (generic message), password mismatch, policy violation; `429` rate limited |

### Story: US-G1-06b — Public Key & JWKS Discovery
*As an API Gateway or resource server, I want to discover the RSA public verification key and JWKS to verify RS256 JWT signatures.*
**Traces to:** Project A JWT Authentication & Security Standard §10, §12

| | |
|---|---|
| **Endpoint (PEM)** | `GET /api/v1/auth/public-key` |
| **Endpoint (JWKS)** | `GET /api/v1/auth/jwks.json` (also aliased at `/.well-known/jwks.json`) |
| **Auth** | None (public) |
| **Response 200 (PEM)** | `{ "data": { "algorithm": "RS256", "format": "X.509", "keyId": "ams-identity-key-2026", "publicKey": "-----BEGIN PUBLIC KEY-----\n..." } }` |
| **Response 200 (JWKS)** | RFC 7517 compliant JWKS: `{ "keys": [ { "kty": "RSA", "use": "sig", "alg": "RS256", "kid": "ams-identity-key-2026", "n": "...", "e": "AQAB" } ] }` |
| **Security rules** | ONLY public keys are exposed. Private keys are strictly confidential and never returned. |

---

## A2. Current User Context

### Story: US-G1-07 — Get My Identity Context
*As an authenticated user, I want to fetch my own identity/role context so the frontend can render role-aware navigation.*
**Traces to:** FR-IAM-024–026, UI-005, UI-018

| | |
|---|---|
| **Endpoint** | `GET /api/v1/users/me` |
| **Auth** | User JWT (self) |
| **Response 200** | `{ "data": { "userId", "email", "firstName", "lastName", "roles": [...], "permissions": [...], "accountStatus": "ACTIVE" } }` |
| **Business rules** | Returns the authenticated user's current identity profile. `permissions` is an array of explicit permission codes (e.g. `["USER_READ", "USER_UPDATE"]`) aggregated from all assigned roles, enabling fine-grained client-side capability checks. Requires an active account. |
| **Errors** | `401` missing/expired Bearer token or invalid token type; `403` suspended/deactivated account |

---

## A3. Administrator — Account Management

### Story: US-G1-08 — Search/List Users
*As a System Administrator, I want to search and filter user accounts so I can manage them.*
**Traces to:** FR-ADM-001, UI-013

| | |
|---|---|
| **Endpoint** | `GET /api/v1/users?query=&status=&requestedRole=&role=&page=&size=` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Query Parameters** | `query`: Free-text search matching case-insensitively across firstName, lastName, fullName, and email.<br>`status`: Lifecycle status filter (`PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`).<br>`requestedRole`: Advisory role requested at self-registration (`OWNER`, `TENANT_RESIDENT`) for pending review workflow.<br>`role`: Assigned system/staff role filter (`SYSTEM_ADMINISTRATOR`, `FINANCE_OFFICER`, etc.).<br>`page`, `size`: Pagination controls (default size 20, sort `createdAt,desc`). |
| **Response 200** | `{ "data": [ { "userId", "email", "fullName", "firstName", "lastName", "accountStatus", "requestedRole", "roles": [...] } ], "meta": { "page": 0, "size": 20, "totalElements": 42 } }` |
| **Errors** | `400` invalid pagination/filter parameters; `401` unauthorized; `403` forbidden (non-admin) |

### Story: US-G1-09 — Get User Details
*As a System Administrator, I want to view a specific user's account details.*
**Traces to:** FR-ADM-001, UI-014

| | |
|---|---|
| **Endpoint** | `GET /api/v1/users/{userId}` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Response 200** | `{ "data": { "userId", "username", "email", "firstName", "lastName", "fullName", "phone", "accountStatus", "requestedRole", "roles": [...], "locked": false, "createdAt", "updatedAt" } }` |
| **Errors** | `401` unauthorized; `403` forbidden; `404` user not found |

### Story: US-G1-10 — Administrator-Created Account (staff)
*As a System Administrator, I want to create staff accounts directly (e.g. Finance Officer) rather than via self-registration.*
**Traces to:** FR-ADM-001, FR-IAM-002

| | |
|---|---|
| **Endpoint** | `POST /api/v1/users` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Request** | `{ "firstName", "lastName", "email", "phone", "temporaryPassword", "initialRoles": [...] }` |
| **Business rules** | Administrator specifies initial credentials including `temporaryPassword` (minimum 8 characters, at least 1 digit). Account is created with `accountStatus = ACTIVE` and `mustChangePassword = true`, requiring a password change upon first login. `initialRoles` optionally assigns valid system/staff roles upon creation; invalid role names return `400 Bad Request`. Emits `USER_CREATED_BY_ADMIN` and `ROLE_ASSIGNED` audit events. |
| **Response 201** | `{ "data": { "userId", "email", "accountStatus": "ACTIVE", "mustChangePassword": true, "roles": [...] } }` |
| **Errors** | `400` validation failure or invalid role in initialRoles; `401` unauthorized; `403` forbidden; `409` duplicate email |

### Story: US-G1-11 — Manage Account Status
*As a System Administrator, I want to activate, suspend, deactivate, or reactivate an account.*
**Traces to:** FR-IAM-007–010, FR-ADM-002, UI-014, UI-019

| | |
|---|---|
| **Endpoint** | `PATCH /api/v1/users/{userId}/status` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Request** | `{ "status": "ACTIVE|SUSPENDED|DEACTIVATED|REJECTED", "reason": "mandatory for SUSPENDED/DEACTIVATED/REJECTED" }` |
| **Business rules** | Allowed transitions: `PENDING_VERIFICATION` -> `[ACTIVE, REJECTED]`, `ACTIVE` -> `[SUSPENDED, DEACTIVATED]`, `SUSPENDED` -> `[ACTIVE, DEACTIVATED]`. Reason is required for destructive/suspension transitions. Deactivation does not delete records (FR-IAM-008). Raises `ACCOUNT_STATUS_CHANGED` audit event. |
| **Response 200** | `{ "data": { "userId", "email", "accountStatus", ... } }` |
| **Errors** | `400` invalid status transition or missing reason; `401` unauthorized; `403` forbidden; `404` user not found |

### Story: US-G1-12 — Assign/Remove Staff Role
*As a System Administrator, I want to assign or remove staff roles so users get the right level of access.*
**Traces to:** FR-IAM-027–029, FR-ADM-003, UC-ADM-001, UI-013/014

| | |
|---|---|
| **Endpoint (assign)** | `POST /api/v1/users/{userId}/roles` — body `{ "role": "FINANCE_OFFICER" }` |
| **Endpoint (remove)** | `DELETE /api/v1/users/{userId}/roles/{roleName}` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Business rules** | Self-assignment or self-removal by an administrator of their own privileged roles is strictly blocked (FR-IAM-027). Role must be one of the 8 valid AMS roles. Multiple roles permitted (FR-IAM-029). Emits `ROLE_ASSIGNED` / `ROLE_REMOVED` audit events. |
| **Response (assign)** | `200 OK` `{ "data": { "userId", "roles": [...] } }` |
| **Response (remove)** | `200 OK` `{ "data": { "userId", "roles": [...] } }` |
| **Errors** | `400` invalid role name or self-assignment/self-removal attempt; `401` unauthorized; `403` forbidden; `404` user not found; `409` role already assigned |

### Story: US-G1-13 — List Available Roles
*As a System Administrator, I want to see the defined system roles when assigning access.*
**Traces to:** FR-IAM-030

| | |
|---|---|
| **Endpoint** | `GET /api/v1/roles` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Response 200** | `{ "data": [ { "id": "<UUID>", "name": "SYSTEM_ADMINISTRATOR", "description": "..." }, ... ] }` (lists all 8 AMS roles) |
| **Errors** | `401` unauthorized; `403` forbidden |

### Story: US-G1-14 — View Audit Log
*As a System Administrator, I want to view identity/account audit history.*
**Traces to:** FR-AUD-001–008, FR-ADM-006, UI-016

| | |
|---|---|
| **Endpoint** | `GET /api/v1/audit-logs?eventType=&subjectUserId=&actorUserId=&from=&to=&page=&size=` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Query Parameters** | `eventType`: Categorized audit event type (e.g. `USER_REGISTERED`, `LOGIN_SUCCESS`, `ACCOUNT_STATUS_CHANGED`, `ROLE_ASSIGNED`, `PASSWORD_RESET_SUCCESS`).<br>`subjectUserId`: UUID of the affected user.<br>`actorUserId`: UUID of the actor/administrator performing the action.<br>`from`: ISO-8601 Instant start timestamp (inclusive).<br>`to`: ISO-8601 Instant end timestamp (inclusive).<br>`page`, `size`: Pagination controls (default size 20, sort `createdAt,desc`). |
| **Response 200** | `{ "data": [ { "id": "<UUID>", "eventType": "...", "subjectUserId": "...", "actorUserId": "...", "previousState": "...", "newState": "...", "reason": "...", "createdAt": "..." } ], "meta": { "page": 0, "size": 20, "totalElements": 150 } }` |
| **Errors** | `400` invalid parameters; `401` unauthorized; `403` forbidden |

---

## A4. Service-to-Service (Internal)

### Story: US-G1-15 — Validate User Context for Other Services
*As another microservice, I want to validate a user's identity/role so I can authorize a cross-domain operation.*
**Traces to:** FR-SEC-003–004, SRS §14.1/14.2, JWT Standard §31, §33

| | |
|---|---|
| **Endpoint** | `GET /internal/v1/users/{userId}` |
| **Auth** | Gateway Service JWT (`type=service`), caller must be in the authorized service allow-list (`resident-management-service`, etc.) |
| **Response 200** | `{ "data": { "userId": "<UUID>", "accountStatus": "ACTIVE", "roles": [...] } }` — **no password, no sensitive profile fields** per JWT Standard §8 |
| **Errors** | `401 Unauthorized` (missing, invalid, or expired Service JWT, or User JWT supplied instead of Service JWT per §25, §33);<br>`403 Forbidden` (valid service token presented, but calling service is not in the allow-list);<br>`404 Not Found` (user ID does not exist);<br>`500 Internal Server Error` |

### Story: US-G1-15b — Internal User Email Update
*As resident-management-service, I want to update a user's verified email address in identity-access-service after email change verification completes.*
**Traces to:** FR-RES-005, SRS §14.2, JWT Standard §31, §33

| | |
|---|---|
| **Endpoint** | `PUT /internal/v1/users/{userId}/email` |
| **Auth** | Gateway Service JWT (`type=service`), caller must be strictly `resident-management-service` |
| **Request** | `{ "email": "resident.updated@ams.lk" }` |
| **Business rules** | Restricted exclusively to `resident-management-service` presenting a valid Service JWT (`type=service`). All other calling services are rejected with `403 Forbidden`. User JWTs are rejected with `401 Unauthorized`. Validates that the new email is not already registered to another account (`409 Conflict`). Updates the user's primary email address and emits an `EMAIL_CHANGED` audit record. |
| **Response 200** | `{ "data": { "userId": "<UUID>", "email": "resident.updated@ams.lk" } }` |
| **Errors** | `400 Bad Request` (invalid email format or empty payload);<br>`401 Unauthorized` (missing, invalid, expired token, or User JWT presented instead of Service JWT);<br>`403 Forbidden` (caller is not `resident-management-service`);<br>`404 Not Found` (target user does not exist);<br>`409 Conflict` (email already in use by another user);<br>`500 Internal Server Error` |

---

# PART B — `resident-management-service`

Owns: `ResidentProfile`, `OwnerProfile`, `StaffProfile`, apartment relationships (Owner / Tenant-Resident), relationship verification workflow, Group 1 in-app notifications.

## B1. Profile Management

### Story: US-G1-16 — View My Profile
*As an authenticated user, I want to view my own profile.*
**Traces to:** FR-RES-001–002, FR-SEC-001, UI-006

| | |
|---|---|
| **Endpoint** | `GET /api/v1/profiles/me` |
| **Auth** | User JWT (self) |
| **Response 200** | `{ "userId","profileType":"RESIDENT\|OWNER\|STAFF","firstName","lastName","phone","contactInfo","statusInfo" }` |

### Story: US-G1-17 — Edit My Profile
*As an authenticated user, I want to edit my permitted profile fields.*
**Traces to:** FR-RES-003–004, UI-007

| | |
|---|---|
| **Endpoint** | `PUT /api/v1/profiles/me` |
| **Auth** | User JWT (self) |
| **Request** | `{ "firstName","lastName","fullName","phone" }` |
| **Business rules** | Email is not editable here (see US-G1-18). Raise `FR-AUD-006` audit event. |
| **Response 200** | Updated profile |
| **Errors** | `400` validation |

### Story: US-G1-18 — Request Email Change
*As an authenticated user, I want to change my account email with verification.*
**Traces to:** FR-RES-005

| | |
|---|---|
| **Endpoint** | `POST /api/v1/profiles/me/email-change` |
| **Auth** | User JWT (self) |
| **Request** | `{ "newEmail" }` → triggers verification step (`PUT /api/v1/profiles/me/email-change/confirm` with `{ "verificationToken" }`) |
| **Business rules** | Email not updated on `identity-access-service` until verification confirmed. |
| **Response 202** | `{ "message": "Verification required" }` |

### Story: US-G1-19 — Admin View of a User's Profile
*As a System Administrator, I want to view any user's profile for account/relationship administration.*
**Traces to:** FR-RES-007, FR-SEC-002, UI-014

| | |
|---|---|
| **Endpoint** | `GET /api/v1/profiles/{userId}` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` (or `APARTMENT_MANAGER` where explicitly permitted) |
| **Business rules** | Normal users cannot browse other residents' profiles (FR-SEC-002) — enforced by role check, not just frontend hiding. |
| **Errors** | `403` insufficient role; `404` |

---

## B2. Apartment Relationship Requests

### Story: US-G1-20 — Submit Apartment Relationship Request
*As a registered user, I want to request an Owner or Tenant/Resident relationship with a unit.*
**Traces to:** FR-REL-001–005, UC-REL-001, UI-010

| | |
|---|---|
| **Endpoint** | `POST /api/v1/relationships` |
| **Auth** | User JWT (self) |
| **Request** | `{ "relationshipType": "OWNER\|TENANT_RESIDENT", "unitReference", "supportingInfo" }` |
| **Business rules** | A user may hold multiple relationships across different units (FR-REL-004). Server sets `status = PENDING`. Unit existence is **not** verified synchronously here — verified later by the admin workflow (see US-G1-22) via Group 2's API, per SRS §14.1 (open cross-team contract). Raise `FR-AUD-004` audit event. |
| **Response 201** | `{ "relationshipId","relationshipType","unitReference","status":"PENDING" }` |
| **Errors** | `400` invalid type/missing unit reference |

### Story: US-G1-21 — View My Relationships
*As a registered user, I want to see all my apartment relationships and their status.*
**Traces to:** FR-REL-004–005, UI-009

| | |
|---|---|
| **Endpoint** | `GET /api/v1/relationships/me` |
| **Auth** | User JWT (self) |
| **Response 200** | `[ { "relationshipId","relationshipType","unitReference","status","decisionReason?" } ]` |

### Story: US-G1-22 — Admin: List/Review Relationship Requests
*As a System Administrator, I want to view pending relationship requests to verify them.*
**Traces to:** FR-REL-006, FR-REL-009–010, UC-REL-002, UI-012

| | |
|---|---|
| **Endpoint** | `GET /api/v1/relationships?status=PENDING&page=&size=` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Response 200** | List with requester summary + submitted unit reference |

### Story: US-G1-23 — Admin: View Relationship Request Details
*As a System Administrator, I want full details of a relationship request, including unit/occupancy info from Group 2, before deciding.*
**Traces to:** FR-REL-009–010, UI-011

| | |
|---|---|
| **Endpoint** | `GET /api/v1/relationships/{relationshipId}` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Business rules** | Server calls Group 2's unit/occupancy validation API (via Gateway, Service JWT) to enrich the response — exact contract is an open item per SRS §18/Group 2. |
| **Response 200** | `{ "relationshipId","requester":{...},"relationshipType","unitReference","unitValidation":{ "exists":true, "ownerMatch":..., "occupancyMatch":... }, "status" }` |
| **Errors** | `404`; `502`/degraded response if Group 2 service unavailable (must not silently approve — NFR-REL-001) |

### Story: US-G1-24 — Admin: Approve Relationship Request
*As a System Administrator, I want to approve a relationship request so the user gets apartment-specific access.*
**Traces to:** FR-REL-007, FR-REL-012–013, UC-REL-002

| | |
|---|---|
| **Endpoint** | `PATCH /api/v1/relationships/{relationshipId}/approve` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Business rules** | Records approver + timestamp (FR-REL-012). Sets `status = APPROVED`. Triggers `FR-NOT-001` notification. Raise `FR-AUD-005` audit event. |
| **Response 200** | Updated relationship record |
| **Errors** | `409` already decided |

### Story: US-G1-25 — Admin: Reject Relationship Request
*As a System Administrator, I want to reject a relationship request with a reason.*
**Traces to:** FR-REL-008, FR-REL-011–012, UC-REL-002

| | |
|---|---|
| **Endpoint** | `PATCH /api/v1/relationships/{relationshipId}/reject` |
| **Auth** | User JWT, role = `SYSTEM_ADMINISTRATOR` |
| **Request** | `{ "reason": "required" }` |
| **Business rules** | Reason is mandatory (FR-REL-011). Records rejector + timestamp (FR-REL-012). Triggers notification + audit event. |
| **Response 200** | Updated relationship record |
| **Errors** | `400` missing reason; `409` already decided |

---

## B3. Service-to-Service (Internal)

### Story: US-G1-26 — Validate Verified Relationship for Other Services
*As another microservice (e.g. billing, operations), I want to confirm a user's verified relationship to a unit before authorizing a domain action.*
**Traces to:** FR-REL-013, FR-SEC-005, SRS §14.1

| | |
|---|---|
| **Endpoint** | `GET /internal/v1/relationships/validate?userId=&unitReference=&relationshipType=` |
| **Auth** | Gateway Service JWT (`type=service`) |
| **Response 200** | `{ "verified": true\|false, "relationshipType", "status" }` |
| **Business rules** | Only `APPROVED` relationships return `verified: true`. This endpoint is the sole way other services confirm apartment-specific access — never inferred from JWT claims (JWT Standard §9). |
| **Errors** | `401`/`403` untrusted caller |

---

## B4. Notifications

### Story: US-G1-27 — View My Notifications
*As an authenticated user, I want to see in-application notifications for Group 1 events.*
**Traces to:** FR-NOT-001, UI-017

| | |
|---|---|
| **Endpoint** | `GET /api/v1/notifications/me?unreadOnly=&page=&size=` |
| **Auth** | User JWT (self) |
| **Response 200** | `[ { "notificationId","type","message","read","createdAt" } ]` — types: registration confirmation, relationship submitted/approved/rejected, account activation/suspension/deactivation, staff role change, password change/reset |

### Story: US-G1-28 — Mark Notification Read
*As an authenticated user, I want to mark a notification as read.*
**Traces to:** FR-NOT-001

| | |
|---|---|
| **Endpoint** | `PATCH /api/v1/notifications/{notificationId}/read` |
| **Auth** | User JWT (self) |
| **Response 204** | — |

---

## 5. Cross-Cutting Notes for Implementation

- All endpoints marked `SYSTEM_ADMINISTRATOR` (or other role) enforce authorization **server-side** independent of frontend route guarding (NFR-SEC-002, UI spec §34).
- All `/internal/v1/...` endpoints only accept a **Gateway Service JWT**; they must reject a Gateway User JWT and vice versa (JWT Standard §7, §33).
- No endpoint returns password hashes, private keys, or another service's business data (JWT Standard §8).
- Every write endpoint that changes account/role/relationship state must emit the corresponding `FR-AUD-xxx` audit record.
- Endpoints referencing Group 2 unit/occupancy data (`US-G1-20`, `US-G1-23`, `US-G1-26`) depend on an **open cross-team API contract** — placeholders here should be confirmed with Group 2 before implementation (SRS §18).

---

## 6. Decisions & Open Items for Sign-Off

### Decided & Implemented
1. **Path and Versioning Conventions:** Confirmed and implemented `/api/v1/...` for external endpoints and `/internal/v1/...` for internal service-to-service communication.
2. **Response Envelope:** Confirmed and implemented unified `ApiResponse` envelope: `{ "data": <payload>, "meta": <metadata-or-null> }` across all endpoints.
3. **Internal Token Type Enforcement:** Enforced strict separation where internal endpoints (`/internal/v1/...`) only accept Gateway Service JWTs (`type=service`) and return `401 Unauthorized` if a User JWT is supplied.
4. **Internal Email Update Contract:** Added `PUT /internal/v1/users/{userId}/email` dedicated exclusively to `resident-management-service` for propagating verified email changes.

### Open Items Pending Cross-Team Sign-Off
1. Confirm exact field names/contract for Group 2's unit/occupancy validation API (`US-G1-20`, `US-G1-23`, `US-G1-26`).
2. Confirm whether `APARTMENT_MANAGER` also gets read access to `GET /api/v1/profiles/{userId}` or if that stays `SYSTEM_ADMINISTRATOR`-only.
3. Confirm OpenAPI/Swagger file location per service repo (`identity-access-service/docs/openapi.yaml`, `resident-management-service/docs/openapi.yaml`).
