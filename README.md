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

### 3. Run the Application

Run the application with the default `local` Spring profile:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
./mvnw.cmd spring-boot:run
```

Flyway will automatically apply database migrations on startup.

### 4. Run Tests

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
