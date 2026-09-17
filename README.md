# identity-access-service

The `identity-access-service` handles user authentication, JWT issuance, Role-Based Access Control (RBAC), and identity/profile validation for the Apartment Management System. It serves as the foundational security and identity provider across all platform microservices, ensuring secure credential management and verified access control.

## Tech Stack

- **Framework**: Spring Boot 3.x
- **Language / Runtime**: Java 21
- **Build Tool**: Maven (with Maven Wrapper `./mvnw`)
- **Database**: MySQL 8.0
- **Database Migrations**: Flyway
- **Security**: Spring Security & JWT (JSON Web Tokens)
- **API Documentation**: springdoc-openapi (Swagger UI)

## API Documentation & Standards

- **Base URL**: `http://localhost:8080/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

## Getting Started (Local Development)

### 1. Prerequisites

- Java 21 JDK installed
- Docker installed and running

### 2. Start MySQL via Docker

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

### 3. Generate Local RSA Key Pair (RS256)

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

### 4. Run the Application

Run the application with the default `local` Spring profile:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
./mvnw.cmd spring-boot:run
```

Flyway will automatically apply database migrations on startup.

### 5. Run Tests

To execute the test suite:

```bash
# Linux / macOS
./mvnw test

# Windows
./mvnw.cmd test
```

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
- **Docker (`docker`)**: Structured single-line JSON output to `stdout` powered by `logstash-logback-encoder` with standard fields (`timestamp`, `level`, `thread`, `logger`, `message`, `service`). All logs stream to container runtime log collectors (12-factor app principle); sensitive credentials/passwords are never logged.
