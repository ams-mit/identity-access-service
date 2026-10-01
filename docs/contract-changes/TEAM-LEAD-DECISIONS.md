# Project A — Identity Access Service: Team Lead Open Decisions

**Date:** 2026-10-01  
**Service:** `identity-access-service` (`kln.ams.identityaccess`)  
**Branch:** `feature/restore-auth-endpoints`  

This document summarizes the three open decisions where contract specifications required alignment or resolution with `PROJECT-A-GLOBAL-API-STANDARD.md` and project constraints. Per the priority rules, the Global API Standard has been implemented pending final Team Lead confirmation.

---

## 1. USR-001 Collection List Format

- **Conflict:**
  - `IDENTITY-ACCESS-SERVICE.md` previously depicted user lists with nested objects or divergent pagination properties.
  - `PROJECT-A-GLOBAL-API-STANDARD.md` mandates top-level `data` array alongside a top-level `pagination` object.
- **Implemented Resolution:**
  - Standardized `GET /api/v1/users` to return the Global API Standard envelope:
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
  - Standard defaults: `page=0`, `size=20`, maximum `size=100`. Sizes exceeding 100 return HTTP 400 `VALIDATION_ERROR`.
  - Reusable generic type `PaginatedApiResponse<T>` / `PaginationMetadata` created for service-wide reusability.
- **Decision Required:** Formal sign-off confirming `USR-001` follows Global API Standard pagination across all consumer services.

---

## 2. Validation Error Details Format

- **Conflict:**
  - Older examples in `IDENTITY-ACCESS-SERVICE.md` used a map format: `details: { "field": "message" }`.
  - `PROJECT-A-GLOBAL-API-STANDARD.md` mandates an array format for field errors: `details: [ { "field": "...", "message": "..." } ]`.
- **Implemented Resolution:**
  - Standardized `GlobalExceptionHandler` and `ApiErrorResponse` so `details` returns a structured list of `{ "field": "...", "message": "..." }`.
  - Retained `details: null` for errors without specific field details.
- **Decision Required:** Formal sign-off confirming field validation error details are uniformly arrays of objects across all API responses.

---

## 3. Spring Boot Version Specification

- **Conflict:**
  - `IDENTITY-ACCESS-SERVICE.md` Section 4.1 specifies `Spring Boot 4.1.1`.
  - Spring Boot 4.x is not a released version (Spring Boot is currently on line 3.x with Spring Framework 6.x).
  - The repository's `pom.xml` uses stable production `Spring Boot 3.3.5` with Java 21.
- **Implemented Resolution:**
  - In accordance with safety rules (*"Do not change the Spring Boot version"*), the service runs on `Spring Boot 3.3.5` (Java 21).
- **Decision Required:** Update the text of `IDENTITY-ACCESS-SERVICE.md` Section 4.1 from `Spring Boot 4.1.1` to `Spring Boot 3.3.5`.
