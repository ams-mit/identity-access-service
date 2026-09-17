# identity-access-service

The `identity-access-service` is the core identity provider and security service for the Apartment Management System (Group 1). It is responsible for user registration, authentication, Role-Based Access Control (RBAC), RS256 JWT issuance and validation, security audit logging, and internal microservice authorization.

## Service Ownership & Scope

The service owns the following functional responsibilities:
- **User Lifecycle & Verification**: Public self-registration (advisory resident-facing roles: `OWNER`, `TENANT_RESIDENT`), administrator-driven verification/approval/rejection workflows, and administrative account provisioning for staff and admin roles (`BUILDING_MANAGER`, `SECURITY_GUARD`, `SYSTEM_ADMINISTRATOR`).
- **Authentication & Brute-Force Defense**: Credential-based authentication with bcrypt password hashing, consecutive failed login attempt tracking, and automatic temporary account lockout.
- **Password Reset**: Cryptographically secure token-based password reset with anti-enumeration protection.
- **Token Issuance & JWKS Distribution**: RS256 asymmetric signing of user access tokens (with user identity and roles) and service-to-service tokens (with registered service identifiers). Public keys are exposed via `/api/v1/auth/public-key` (PEM) and `/.well-known/jwks.json` (JWKS RFC 7517) for downstream validation across platform microservices.
- **Internal Microservice Authorization**: Secured internal user validation endpoint (`/internal/v1/users/{userId}`) restricted strictly to callers presenting valid Service JWTs.
- **Security Audit Logging**: Append-only event trail (`audit_events`) capturing authentication successes/failures, account status changes, and role assignments, queryable and pageable by `SYSTEM_ADMINISTRATOR` users.
- **Abuse Prevention**: In-memory sliding-window rate limiting on unauthenticated public endpoints (`/register`, `/login`, `/forgot-password`, `/reset-password`).

## Tech Stack

- **Framework**: Spring Boot 3.3.5
- **Language / Runtime**: Java 21
- **Build Tool**: Maven (with Maven Wrapper `./mvnw` / `mvnw.cmd`)
- **Database**: MySQL 8.0
- **Database Migrations**: Flyway
- **Security**: Spring Security & RS256 JWT (JSON Web Tokens)
- **API Documentation**: springdoc-openapi (Swagger UI & OpenAPI v1.0.0)
- **Logging**: Logback with `logstash-logback-encoder` (profile-aware console / structured JSON)

## API Documentation & Standards

- **Base URL**: `http://localhost:8080/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI v1.0 Specification**: `http://localhost:8080/v3/api-docs`

## Getting Started (Local Development)

### 1. Prerequisites

- **Java 21 JDK** installed and configured (`JAVA_HOME`).
- **Docker** installed and running (required for local MySQL database and integration test execution).
- **Maven Wrapper** is included (`./mvnw` on Linux/macOS, `.\mvnw.cmd` on Windows) — no standalone Maven installation is required.

### 2. Clone and Setup Environment

Clone the repository and prepare the local environment file:

```bash
git clone <repository-url>
cd identity-access-service
cp .env.example .env
```

Verify or configure the variables in `.env`:
- `DB_URL`: JDBC URL for MySQL (default: `jdbc:mysql://localhost:3306/identity_db`)
- `DB_USERNAME` / `DB_PASSWORD`: MySQL database credentials (default: `identity_user` / `identity_pass`)
- `SERVER_PORT`: Application HTTP port (default: `8080`)
- `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH`: RSA PEM key paths (default: `classpath:certs/private_key.pem`, `classpath:certs/public_key.pem`)

### 3. Start MySQL via Docker

Start the dedicated MySQL container for local development:

```bash
docker run --name identity-mysql \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -e MYSQL_DATABASE=identity_db \
  -e MYSQL_USER=identity_user \
  -e MYSQL_PASSWORD=identity_pass \
  -p 3306:3306 \
  -d mysql:8.0
```

### 4. Generate Local RSA Key Pair (RS256)

The service signs JWTs using RS256 asymmetric keys and requires a private/public key pair. You can generate a 2048-bit RSA key pair in PKCS#8 / X.509 PEM format using the included generator:

```bash
# Linux / macOS
./mvnw compile exec:java -Dexec.mainClass="lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyPairGenerator"

# Windows
.\mvnw.cmd compile exec:java -Dexec.mainClass="lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyPairGenerator"
```

This generates `certs/private_key.pem` and `certs/public_key.pem` in the project root. Alternatively, using OpenSSL:

```bash
mkdir -p certs
openssl genpkey -algorithm RSA -out certs/private_key.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in certs/private_key.pem -out certs/public_key.pem
```

> **Note on Key Provisioning & Security**:
> - Key files in `certs/` and `*.pem` are ignored by git and must **never** be committed.
> - **Local Development**: Generate a keypair using the commands above and configure paths in your `.env` file via `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` (or rely on default `classpath:certs/`).
> - **Production / Deployed Environments**: A real, cryptographically secure 2048-bit (or 4096-bit) RSA keypair must be provisioned before application startup via your deployment secrets infrastructure (e.g. Kubernetes Secrets, AWS Secrets Manager, HashiCorp Vault, or mounted secret volumes). Provide the file or URI locations via `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` environment variables.
> - **Automated Tests (CI & Local)**: Test suites (`IdentityAccessServiceApplicationTests` and `AbstractIntegrationTest`) automatically provision ephemeral, throwaway RSA keypairs at test runtime, ensuring automated builds and CI runs remain completely self-contained with no pre-existing key files required.

### 5. Run the Application

Run the application with the default `local` Spring profile:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
.\mvnw.cmd spring-boot:run
```

Flyway will automatically apply database migrations on startup.

### 6. Run Tests

To execute the full test suite:

```bash
# Linux / macOS
./mvnw test

# Windows
.\mvnw.cmd test
```

> **Integration Tests & Docker Requirement**:
> - Unit tests run entirely in-memory without external dependencies.
> - Integration tests (`*IntegrationTest`) use **Testcontainers** to spin up an isolated, genuine MySQL 8.0 Docker container. Docker must be running on your system for integration tests to execute.
> - **Windows + Docker Desktop Workaround**: If Testcontainers fails to connect to Docker on Windows, configure the Docker named pipe connection by adding `docker.host=npipe:////./pipe/docker_engine` to `%USERPROFILE%\.testcontainers.properties` or by setting the environment variable `DOCKER_HOST=npipe:////./pipe/docker_engine`.

## Docker Build

To package and build the container image locally:

```bash
docker build -t identity-access-service .
```

The container image includes an integrated `HEALTHCHECK` checking `/actuator/health/liveness` every 30 seconds.

## Health & Readiness Probes

Spring Boot Actuator is configured with safe-by-default exposure (only `health` and `info` exposed over HTTP, details never leaked to unauthenticated callers).

| Endpoint | Purpose | Checks |
| :--- | :--- | :--- |
| `GET /actuator/health/liveness` | Container liveness | Confirms the Spring Boot application context is running and responsive. Used by Docker/Kubernetes to restart crashed containers. |
| `GET /actuator/health/readiness` | Traffic readiness | Verifies the service is ready to accept user traffic, validating active MySQL database connectivity via `DataSourceHealthIndicator`. |
| `GET /actuator/health` | Overall health summary | High-level status indicator (`{"status":"UP"}`). |

## Logging & Observability

Logging is profile-aware via `logback-spring.xml`:
- **Local (`!docker`)**: Human-readable, colorized standard console logging optimized for local developer experience.
- **Docker (`docker`)**: Structured single-line JSON output to `stdout` powered by `logstash-logback-encoder` with standard fields (`timestamp`, `level`, `thread`, `logger`, `message`, `service`). All logs stream to container runtime log collectors (12-factor app principle); sensitive credentials and passwords are never logged.

## Database Schema & Migrations

Database schema evolution is managed via Flyway versioned migration scripts in `src/main/resources/db/migration/`:
- `V1__init_schema.sql`: Base tables (`users`, `roles`, `user_roles`, `refresh_tokens`, `account_status_audit`).
- `V2__seed_roles.sql`: Standard system roles (`OWNER`, `TENANT_RESIDENT`, `BUILDING_MANAGER`, `SECURITY_GUARD`, `SYSTEM_ADMINISTRATOR`).
- `V3__add_failed_login_tracking.sql`: Failed login tracking (`failed_login_attempts`, `lockout_until`).
- `V4__create_password_reset_tokens_table.sql`: Password reset token lifecycle table.
- `V5__add_unique_constraint_to_password_reset_tokens.sql`: Token uniqueness constraints.
- `V6__add_rejected_status.sql`: Account rejection support (`REJECTED` status).
- `V7__seed_admin_user.sql`: Bootstrap default system administrator account.
- `V8__add_user_roles_unique_constraint.sql`: Prevent duplicate role assignments per user.
- `V9__create_audit_events_table.sql`: Multi-criteria security and lifecycle audit log table (`audit_events`).

> **Schema Clarification — Audit Table**:
> The legacy `account_status_audit` table created in `V1` is **superseded** by the `audit_events` table introduced in `V9`. The active application persistence layer (`AuditEvent` entity and `AuditEventRepository`) writes all security, status change, role assignment, and authentication audit records exclusively to `audit_events`.

## Known Limitations & Production Gaps

The following architectural decisions and prototype limitations apply to `identity-access-service`:
1. **Password Reset Email Delivery**: In the current version, actual email/SMS transport is out of scope. Password reset tokens are generated, hashed, and stored securely, and for manual testing/prototyping purposes, the raw token is emitted as a `DEV-ONLY` diagnostic log entry. In production, an SMTP client or notification service integration would deliver the token.
2. **Rate Limiting & Proxy IP Resolution**: Rate limiting on public auth endpoints uses an in-memory sliding-window counter keyed strictly by remote IP (`HttpServletRequest.getRemoteAddr()`). It intentionally does not inspect `X-Forwarded-For` headers to prevent header spoofing. In production deployments behind a reverse proxy or API Gateway, gateway-level rate limiting or trusted proxy header resolution should be configured.
3. **Stateless JWT Logout**: Logout (`POST /api/v1/auth/logout`) is client-side in the current stateless JWT architecture (the client purges the stored Bearer token). Server-side token blacklisting or revocation lists (e.g. backed by Redis) are out of scope for this release.
