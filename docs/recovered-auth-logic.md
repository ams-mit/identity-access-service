# Phase 1 Forensic Report: Recovered Authentication Logic

**Service**: `identity-access-service`  
**Package Base**: `kln.ams.identityaccess` (formerly `lk.ac.kelaniya.ams.identity_access_service`)  
**Java Version**: 21  
**Framework**: Spring Boot 3.3.5 / Spring Security 6  
**Database**: MySQL (`identity_access_db`)  
**Target Specification Standards**:
- Canonical Contract: `IDENTITY-ACCESS-SERVICE.md`
- API Standard: `PROJECT-A-GLOBAL-API-STANDARD.md`
- Security Standard: `PROJECT-A-JWT-SECURITY-STANDARD.md`

---

## 1. Executive Summary

During the architectural refactoring to the Project A canonical contract (Pull Request #51: `ams-mit/feature/iam-canonical-contract`), five essential authentication and password-management endpoints were removed when the legacy package `lk.ac.kelaniya.ams.identity_access_service` was replaced by `kln.ams.identityaccess`:
1. `POST /api/v1/auth/register` (Self-registration)
2. `POST /api/v1/auth/forgot-password` (Password reset initiation with anti-enumeration)
3. `POST /api/v1/auth/reset-password` (Password reset completion via cryptographic token)
4. `POST /api/v1/auth/logout` (Stateless logout, canonical ID: `AUTH-003`)
5. `PUT /api/v1/auth/me/password` (Self-service password update; formerly `PUT /api/v1/users/me/password`)

This report presents a complete forensic analysis of the git history, extracts the exact business logic and test coverage from the last pre-refactor commit (`58923a5`), and establishes a side-by-side **Conflict Table** comparing legacy behavior with the Project A canonical contract to guide Phase 2 implementation.

---

## 2. Git Forensic Analysis & Source References

### 2.1 Key Commits

| Commit Hash | Commit Type | Description |
| :--- | :--- | :--- |
| `58923a5f9068a48d5765266ce5c681a196ec4d21` | **Last Working Pre-Refactor Commit** | The commit immediately prior to the refactor branch where all 5 endpoints, entity definitions, Flyway tables, and integration tests existed. Parent of `9bbb8ed`. |
| `9bbb8ed4fa4d0059ede92f8352c98c4cff79456f` | **Refactor Removal Commit (Main)** | `chore: remove legacy lk.ams main package` — Removed 97 files (6,339 lines), including legacy controllers, services, repositories, and entities. |
| `ce22f9d3b1e793910ad59616e1db97bc8a7e8e19` | **Refactor Removal Commit (Tests)** | `chore: remove legacy lk.ams test package` — Removed 40 test files covering registration, reset password, logout, and change password flows. |
| `6cab5fa49e3bf8e8c897f1f96cb10da186b5155f` | **Schema Migration Drop** | `feat(db): add V10 canonical target-state migration` — Dropped legacy table `password_reset_tokens`. |
| `9ae34ca7b96054fa82944b94f1c1fca58428801f` | **Refactor Merge Commit** | `Merge pull request #51 from ams-mit/feature/iam-canonical-contract` — Merged the canonical refactor into the mainline branch. |

### 2.2 Recovered Source Files Analyzed (from `58923a5`)

- **Controllers**:
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/controller/AuthController.java` (Endpoints: register, login, forgot-password, reset-password, logout)
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/controller/UserController.java` (Endpoint: `PUT /api/v1/users/me/password`)
- **DTOs**:
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/dto/request/RegisterRequest.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/dto/request/ForgotPasswordRequest.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/dto/request/ResetPasswordRequest.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/dto/request/ChangePasswordRequest.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/dto/response/RegisterResponse.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/dto/response/MessageResponse.java`
- **Entities & Repositories**:
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/entity/PasswordResetToken.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/entity/AccountStatus.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/repository/PasswordResetTokenRepository.java`
- **Services**:
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/service/AuthService.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/service/UserService.java`
- **Security & Exceptions**:
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/security/SecurityConfig.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/exception/GlobalExceptionHandler.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/exception/InvalidResetTokenException.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/exception/DuplicateEmailException.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/exception/PasswordMismatchException.java`
  - `src/main/java/lk/ac/kelaniya/ams/identity_access_service/exception/SamePasswordException.java`
- **Database Migrations**:
  - `src/main/resources/db/migration/V8__create_password_reset_tokens_table.sql`
  - `src/main/resources/db/migration/V10__canonical_target_state.sql`
- **Tests**:
  - `src/test/java/lk/ac/kelaniya/ams/identity_access_service/controller/AuthControllerTest.java`
  - `src/test/java/lk/ac/kelaniya/ams/identity_access_service/controller/UserControllerTest.java`
  - `src/test/java/lk/ac/kelaniya/ams/identity_access_service/service/PasswordResetRoundTripTest.java`
  - `src/test/java/lk/ac/kelaniya/ams/identity_access_service/service/ChangePasswordRoundTripTest.java`
  - `src/test/java/lk/ac/kelaniya/ams/identity_access_service/integration/PasswordResetLifecycleIntegrationTest.java`
  - `src/test/java/lk/ac/kelaniya/ams/identity_access_service/integration/UserRegistrationIntegrationTest.java`

---

## 3. Summary of Old Endpoint Logic

### 3.1 `POST /api/v1/auth/register` (Self-Registration)
- **Controller Method**: `AuthController.register(@Valid @RequestBody RegisterRequest request)`
- **HTTP Status**: `201 Created`
- **Validation Annotations**:
  - `firstName`: `@NotBlank`, `@Size(max = 100)`
  - `lastName`: `@NotBlank`, `@Size(max = 100)`
  - `email`: `@NotBlank`, `@Email`, `@Size(max = 150)`
  - `phone`: `@Size(max = 20)` (optional)
  - `password`: `@NotBlank`, `@Size(min = 8, max = 100)`, `@Pattern(regexp = "^(?=.*[0-9]).{8,}$", message = "Password must be at least 8 characters long and contain at least one numeric digit")`
  - `confirmPassword`: `@NotBlank`
  - `requestedRole`: `@NotBlank`, `@Pattern(regexp = "^(OWNER|TENANT_RESIDENT)$")`
- **Service Logic (`AuthService.register`)**:
  1. Compares `password` with `confirmPassword`; throws `PasswordMismatchException` (400) if mismatched.
  2. Normalizes email via `trim().toLowerCase(Locale.ROOT)`.
  3. Checks duplicate email via `userRepository.existsByEmail(email)`; throws `DuplicateEmailException` (409) if existing. Catches concurrent DB collisions as `DataIntegrityViolationException` (409).
  4. Encodes password using `BCryptPasswordEncoder`.
  5. Instantiates `User` with `accountStatus = AccountStatus.PENDING_VERIFICATION`, `username = email`, `requestedRole = request.getRequestedRole()`, `failedAttemptCount = 0`.
  6. Did **not** grant database roles (advisory only).
  7. Returns `RegisterResponse` containing `userId`, `email`, `accountStatus`, `requestedRole`. Never returned password or password hash.
- **Security Config**: Explicitly configured as `.permitAll()`.

---

### 3.2 `POST /api/v1/auth/forgot-password`
- **Controller Method**: `AuthController.forgotPassword(@Valid @RequestBody ForgotPasswordRequest request)`
- **HTTP Status**: `200 OK`
- **Validation Annotations**:
  - `email`: `@NotBlank`, `@Email`, `@Size(max = 150)`
- **Service Logic (`AuthService.forgotPassword`)**:
  1. Enforces **strict anti-enumeration**: unconditionally returns HTTP 200 with generic message: `"If an account is associated with this email, instructions will be provided."`.
  2. Normalizes email via `trim().toLowerCase(Locale.ROOT)`.
  3. Queries `userRepository.findByEmail(email)`.
  4. Only if user exists AND `user.getAccountStatus() == AccountStatus.ACTIVE`:
     - Calls `passwordResetTokenRepository.invalidateAllActiveTokensForUser(user, Instant.now())` to revoke previous unused tokens.
     - Generates 32 bytes of cryptographic entropy (`SecureRandom`), URL-safe Base64 encoded without padding.
     - Computes SHA-256 hex digest of the raw token.
     - Sets expiration to `Instant.now().plus(Duration.ofMinutes(tokenValidityMinutes))` (legacy default 30 min).
     - Persists `PasswordResetToken` record (`user`, `tokenHash`, `expiresAt`, `usedAt = null`).
     - Emits `[DEV-ONLY]` diagnostic log with raw token value for Swagger/Postman testing (in production profile, token value is suppressed).
  5. Inactive, suspended, or nonexistent users receive the identical 200 response without generating a token.
- **Security Config**: Configured as `.permitAll()`.

---

### 3.3 `POST /api/v1/auth/reset-password`
- **Controller Method**: `AuthController.resetPassword(@Valid @RequestBody ResetPasswordRequest request)`
- **HTTP Status**: `200 OK`
- **Validation Annotations**:
  - `resetToken`: `@NotBlank`
  - `newPassword`: `@NotBlank`, `@Size(min = 8, max = 100)`, `@Pattern(regexp = "^(?=.*[0-9]).{8,}$")`
  - `confirmNewPassword`: `@NotBlank`
- **Service Logic (`AuthService.resetPassword`)**:
  1. Compares `newPassword` with `confirmNewPassword`; throws `PasswordMismatchException` (400) if mismatched.
  2. Hashes incoming raw `resetToken` with SHA-256.
  3. Queries `passwordResetTokenRepository.findByTokenHash(tokenHash)`.
  4. Enforces anti-enumeration across all token failure cases (token missing, already used `usedAt != null`, expired `expiresAt.isBefore(now)`, or user not in `ACTIVE` state) by throwing identical generic `InvalidResetTokenException` (400): `"Invalid or expired password reset token."`.
  5. Upon validation success:
     - Encodes new password with `BCryptPasswordEncoder` and updates `user.passwordHash`.
     - Resets `user.failedAttemptCount = 0`, `user.lockedUntil = null`, and `user.mustChangePassword = false`.
     - Saves updated `User`.
     - Sets `token.usedAt = Instant.now()` and saves token entity.
     - Calls `passwordResetTokenRepository.invalidateAllActiveTokensForUser(user, now)` to invalidate all remaining tokens.
  6. Returns `MessageResponse` with `"Password has been reset successfully."`.
- **Security Config**: Configured as `.permitAll()`.

---

### 3.4 `POST /api/v1/auth/logout` (AUTH-003)
- **Controller Method**: `AuthController.logout()`
- **HTTP Status**: `204 No Content`
- **Request Body**: None (authenticated via HTTP `Authorization: Bearer <token>`).
- **Service Logic**:
  - Stateless architecture adhering to `PROJECT-A-JWT-SECURITY-STANDARD.md`.
  - Client discards the token. No database state changes or token revocation tables required.
  - Returns `ResponseEntity.noContent().build()`.
- **Security Config**: Configured as `.authenticated()` (requires valid User JWT; returns 401 if unauthenticated or invalid/expired token).

---

### 3.5 `PUT /api/v1/auth/me/password` (Formerly `PUT /api/v1/users/me/password`)
- **Controller Method**: Formerly in `UserController.changePassword(@AuthenticationPrincipal Object principal, @Valid @RequestBody ChangePasswordRequest request)`
- **HTTP Status**: `204 No Content`
- **Validation Annotations**:
  - `currentPassword`: `@NotBlank`
  - `newPassword`: `@NotBlank`, `@Size(min = 8, max = 100)`, `@Pattern(regexp = "^(?=.*[0-9]).{8,}$")`
  - `confirmNewPassword`: `@NotBlank`
- **Service Logic (`UserService.changePassword`)**:
  1. Extracts `UserPrincipal` from security context; rejects `ServicePrincipal` with 401.
  2. Verifies current password against stored hash using `passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())`. If mismatch, throws `InvalidCredentialsException` (401) with `"Current password is incorrect."`.
  3. Verifies `newPassword.equals(confirmNewPassword)`. If mismatch, throws `PasswordMismatchException` (400) with `"New password and confirm password do not match"`.
  4. Verifies `newPassword` differs from `currentPassword` and current hash. If identical, throws `SamePasswordException` (400) with `"New password must differ from current password"`.
  5. Encodes `newPassword` with BCrypt and updates `user.passwordHash`.
  6. Clears `mustChangePassword = false`.
  7. Persists updated `User`.
- **Security Config**: Protected endpoint requiring authenticated User JWT.

---

## 4. Conflict Table: Old Behavior vs. New Canonical Contract

| Dimension | Old Implementation (`58923a5`) | New Canonical Contract (`IDENTITY-ACCESS-SERVICE.md` & Global Standards) | Adaptation Required in Phase 2 |
| :--- | :--- | :--- | :--- |
| **Change Password Endpoint Route** | `PUT /api/v1/users/me/password` (located in `UserController`) | `PUT /api/v1/auth/me/password` (canonical API ID: `AUTH-007`, in `AuthController`) | Move change-password endpoint to `AuthController` under `/api/v1/auth/me/password`. |
| **API Inventory & Canonical IDs** | No formal IDs, or ad-hoc IDs | Assigned canonical IDs:<br>• `AUTH-003`: Logout<br>• `AUTH-004`: Register<br>• `AUTH-005`: Forgot Password<br>• `AUTH-006`: Reset Password<br>• `AUTH-007`: Change Password | Document all 5 endpoints in OpenAPI annotations with their respective `AUTH-00x` operation tags and summaries. |
| **Success Response Envelope** | Custom `ApiResponse<T>`: `{ data: T }` or direct `MessageResponse` `{ message }` | Canonical `ApiResponse<T>`: `{ success: true, message: String, data: T, timestamp: String, requestId: String }` | Use canonical `ApiResponse.ok(message, data)`. For 204 endpoints (logout, me/password), return `ResponseEntity.noContent().build()`. |
| **Error Response Envelope** | Custom `ErrorResponse`: `{ error: { code, message }, timestamp }` | Canonical `ApiErrorResponse`: `{ success: false, message: String, error: { code, details }, timestamp, requestId }` | Route all error responses through existing `@RestControllerAdvice` (`GlobalExceptionHandler`) throwing subclasses of `ApiException`. |
| **Canonical Error Codes** | Ad-hoc error codes: `EMAIL_ALREADY_EXISTS`, `PASSWORD_MISMATCH`, `SAME_PASSWORD`, `ACCOUNT_PENDING_VERIFICATION`, etc. | Canonical standardized error codes:<br>• `VALIDATION_ERROR`<br>• `USER_ALREADY_EXISTS`<br>• `INVALID_CREDENTIALS`<br>• `INVALID_RESET_TOKEN`<br>• `ACCOUNT_INACTIVE`<br>• `INVALID_TOKEN` | Map domain errors to canonical codes: duplicate email $\rightarrow$ `USER_ALREADY_EXISTS` (409); password mismatch / same password / short password $\rightarrow$ `VALIDATION_ERROR` (400); invalid reset token $\rightarrow$ `INVALID_RESET_TOKEN` (400); inactive account $\rightarrow$ `ACCOUNT_INACTIVE` (403). |
| **Account Lifecycle Statuses** | Six statuses: `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`, `REJECTED`, `LOCKED` | Exactly three statuses: `ACTIVE`, `INACTIVE`, `SUSPENDED` | Self-registered users default to `INACTIVE`. Only `ACTIVE` accounts can authenticate or request password resets. Inactive users receive `ACCOUNT_INACTIVE` (403). |
| **Registration Role Assignment** | Client supplied `requestedRole` (`OWNER` or `TENANT_RESIDENT`) which was advisory only; zero roles granted in DB. | Client must **not** supply roles. Service automatically assigns canonical role `TENANT_RESIDENT`. | Remove `requestedRole` field from `RegisterRequest`. Look up canonical `TENANT_RESIDENT` from `RoleRepository` and link via `UserRole` on user creation. |
| **Login Credential Field** | Field was `email` | Canonical field is `username`. On register, set `username = email` to align with the current `User` entity. | Register sets `user.setUsername(email.toLowerCase())` and `user.setEmail(email.toLowerCase())`. Login checks `username` (which holds normalized email). |
| **Reset Token Storage & Validity** | Stored in `password_reset_tokens` (dropped in `V10`). Expiration default was 30 minutes. | Restored in `password_reset_tokens` via Flyway `V11`. Configurable expiry with 15 minutes default (`${auth.password-reset.token-validity-minutes:15}`). | Create Flyway migration `V11__restore_password_reset_tokens_table.sql`. Maintain SHA-256 hashed storage and single-use invalidation. |
| **Audit Logging** | Injected `AuditService` and persisted to `audit_events` table (V9, dropped in V10). | Audit events table and service removed in canonical contract (outside IAM service scope). | Do **not** restore `audit_events` or `AuditService`. Use structured SLF4J application logging instead. |
| **Resident Profile Management** | Separate endpoints existed in early design. | Owned strictly by `resident-management-service`. | Do not add profile fields (e.g. apartment unit, lease details) to IAM service. |

---

## 5. Security & Public Path Authorization Matrix

In the current `SecurityConfig`, the endpoints must be secured as follows:

| Endpoint | Method | Path | Auth Requirement | Security Config Rule |
| :--- | :--- | :--- | :--- | :--- |
| `AUTH-001` | `POST` | `/api/v1/auth/login` | Public | `.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()` |
| `AUTH-002` | `GET` | `/api/v1/auth/me` | Authenticated User | `.requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()` |
| `AUTH-003` | `POST` | `/api/v1/auth/logout` | Authenticated User | `.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()` |
| `AUTH-004` | `POST` | `/api/v1/auth/register` | Public | `.requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()` |
| `AUTH-005` | `POST` | `/api/v1/auth/forgot-password` | Public | `.requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()` |
| `AUTH-006` | `POST` | `/api/v1/auth/reset-password` | Public | `.requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()` |
| `AUTH-007` | `PUT` | `/api/v1/auth/me/password` | Authenticated User | `.requestMatchers(HttpMethod.PUT, "/api/v1/auth/me/password").authenticated()` |

---

## 6. Phase 2 Implementation Plan

Upon your approval, Phase 2 will execute on branch `feature/restore-auth-endpoints`:

1. **Database Migration (`V11__restore_password_reset_tokens_table.sql`)**:
   - Recreate `password_reset_tokens` table with columns: `id BINARY(16)`, `user_id BINARY(16)`, `token_hash VARCHAR(255)`, `created_at DATETIME`, `expires_at DATETIME`, `used_at DATETIME`, FK constraint to `users(id)`, and indexes on `token_hash` and `user_id`.
2. **Entity & Repository**:
   - Create `PasswordResetToken` in `kln.ams.identityaccess.entity`.
   - Create `PasswordResetTokenRepository` in `kln.ams.identityaccess.repository`.
3. **DTOs (`kln.ams.identityaccess.dto.auth`)**:
   - `RegisterRequest` (`firstName`, `lastName`, `email`, `phone`, `password`, `confirmPassword` — no `requestedRole`).
   - `RegisterResponse` (`userId`, `username`, `email`, `status`, `assignedRole` — never password hash).
   - `ForgotPasswordRequest` (`email`).
   - `ResetPasswordRequest` (`resetToken`, `newPassword`, `confirmNewPassword`).
   - `ChangePasswordRequest` (`currentPassword`, `newPassword`, `confirmNewPassword`).
   - Informational message payload or reuse existing DTO patterns.
4. **Exceptions & Global Exception Handler**:
   - Align exception hierarchy with `ApiException`:
     - `UserAlreadyExistsException` $\rightarrow$ `USER_ALREADY_EXISTS` (409)
     - `InvalidResetTokenException` $\rightarrow$ `INVALID_RESET_TOKEN` (400)
     - `PasswordValidationException` $\rightarrow$ `VALIDATION_ERROR` (400)
   - Ensure all responses utilize existing `GlobalExceptionHandler` returning `ApiErrorResponse`.
5. **Service Layer Updates**:
   - Implement `register`, `forgotPassword`, `resetPassword`, and `changePassword` methods in `AuthService`.
   - Ensure self-registration assigns canonical role `TENANT_RESIDENT` and sets account status to `INACTIVE`.
   - Configure reset token validity to 15 minutes (`auth.password-reset.token-validity-minutes:15`).
6. **Controller Layer**:
   - Add the 5 endpoints to `AuthController` with complete OpenAPI documentation matching contract IDs `AUTH-003` through `AUTH-007`.
7. **Security Configuration**:
   - Update `SecurityConfig` authorizeHttpRequests.
8. **Automated Testing**:
   - Add unit, slice, and integration tests verifying all success paths, validation failures, anti-enumeration, token expiration/reuse, duplicate emails, and inactive account login rejection.

---

**Report Status**: Completed. Ready for review and lead approval.
