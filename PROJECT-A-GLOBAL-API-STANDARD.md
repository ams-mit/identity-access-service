# Project A — Global API Standard

## Purpose

This document defines the common API contract used by all Project A backend services and the API Gateway.

All services must use the same conventions for:

- REST APIs;
- JSON requests and responses;
- URL and resource naming;
- authentication and authorization integration;
- response envelopes;
- error handling;
- pagination, filtering and sorting;
- validation;
- HTTP status codes;
- cross-service communication;
- timeouts and dependency failures;
- request tracing;
- logging;
- health checks;
- OpenAPI/Swagger documentation;
- API testing; and
- contract changes.

Service-specific business rules and endpoint definitions belong in the corresponding service contract.

JWT implementation and trust rules belong in `PROJECT-A-JWT-SECURITY-STANDARD.md`.

Cross-service provider/consumer relationships belong in `PROJECT-A-CROSS-SERVICE-API-REGISTRY.md`.

---

## Technology standard

| Area | Standard |
|---|---|
| Protocol | HTTP/HTTPS |
| API style | REST |
| Data format | JSON |
| Backend | Spring Boot |
| API Gateway | Central API Gateway |
| Authentication | JWT Bearer |
| API documentation | OpenAPI / Swagger |
| API testing | Postman + automated tests |
| Database | MySQL |
| API versioning | URI versioning |
| Character encoding | UTF-8 |
| Date/time | ISO-8601 |
| Identifiers | UUID |
| JSON naming | camelCase |
| URL naming | kebab-case |
| Resource naming | plural nouns |

---

## API Gateway

The shared frontend communicates through the API Gateway rather than directly calling backend services.

```text
Frontend
    |
    v
API Gateway
    |
    +-------------------------------+
    |               |               |
    v               v               v
Identity        Property         Billing
Services        Services         Services
    |               |               |
    +---------------+---------------+
                    |
                    v
             Other Services
```

The API Gateway provides the common entry point for:

- routing;
- authentication integration;
- Gateway-level authorization checks where applicable;
- request ID propagation;
- centralized API access;
- Gateway JWT handling;
- consistent downstream failure handling;
- configured CORS/rate limiting where applicable;
- health aggregation.

The Gateway must not contain business-domain logic or own business-domain data.

---

## API versioning

All stable Project A APIs use:

```text
/api/v1
```

Examples:

```http
GET /api/v1/residents
GET /api/v1/residents/{residentId}
POST /api/v1/residents
```

Breaking changes must not be silently introduced into `/api/v1`.

A breaking contract change requires a new major API version or an explicitly approved migration strategy.

Example:

```text
/api/v2/...
```

A breaking change includes:

- removing a response field;
- renaming a field;
- changing a field type;
- changing required/optional behavior;
- changing endpoint meaning;
- changing authentication requirements;
- changing an enum value in a way that breaks consumers;
- changing the business meaning of an existing field.

---

## URL naming

Use REST resource names rather than action-based URLs.

### Canonical

```http
GET    /api/v1/residents
GET    /api/v1/residents/{residentId}
POST   /api/v1/residents
PUT    /api/v1/residents/{residentId}
PATCH  /api/v1/residents/{residentId}
DELETE /api/v1/residents/{residentId}
```

### Avoid

```http
GET  /api/v1/getResidents
POST /api/v1/createResident
POST /api/v1/resident/create
GET  /api/v1/getAllResidents
```

Use:

- plural nouns for collections;
- kebab-case for multi-word URL resources;
- path parameters for resource identifiers;
- query parameters for filtering, searching, sorting and pagination.

---

## Internal API namespace

Cross-service provider APIs use:

```text
/api/v1/internal/...
```

Example:

```http
GET /api/v1/internal/units/{unitId}/exists
```

Internal APIs remain documented API contracts.

They must not be hidden, undocumented endpoints.

Public resource APIs use:

```text
/api/v1/...
```

The provider service owns the canonical internal API contract.

Consumers must not redefine the provider's response schema.

---

## HTTP methods

| Method | Purpose |
|---|---|
| `GET` | Retrieve resource(s) |
| `POST` | Create a resource or perform a non-idempotent operation |
| `PUT` | Replace a resource |
| `PATCH` | Partially update a resource |
| `DELETE` | Delete/deactivate a resource where appropriate |

Use domain-specific status/deactivation operations instead of physical deletion when historical or audit information must be preserved.

---

## Standard request headers

Requests should use:

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <JWT>
X-Request-ID: <UUID>
```

### Content-Type

Requests containing JSON bodies must use:

```http
Content-Type: application/json
```

### Accept

Clients should request:

```http
Accept: application/json
```

### Authorization

Protected APIs use:

```http
Authorization: Bearer <JWT>
```

### X-Request-ID

Every request must have a request ID.

If the client provides a valid request ID, the Gateway propagates it downstream.

If one is not provided, the Gateway or receiving entry point generates one.

The same request ID must be propagated through downstream synchronous calls.

Example:

```text
Frontend
   |
   | X-Request-ID: 7f83a9b2-...
   v
API Gateway
   |
   | X-Request-ID: 7f83a9b2-...
   v
Service A
   |
   | X-Request-ID: 7f83a9b2-...
   v
Service B
```

---

## Authentication

Protected APIs use JWT Bearer authentication.

```http
Authorization: Bearer <JWT>
```

Authentication and JWT trust are defined by the project security standard.

At the API contract level:

- protected endpoints must document authentication;
- public endpoints must explicitly state that authentication is not required;
- services must reject invalid authentication;
- authentication failures use `401 Unauthorized`.

---

## Authorization

Authentication and authorization are separate.

Authorization follows:

```text
Valid authentication
        |
        v
Authenticated identity
        |
        v
Canonical role
        |
        v
Required business/domain relationship
        |
        v
Endpoint authorization
        |
        v
Allow / Deny
```

Role authorization alone is insufficient when an endpoint requires a specific business relationship.

Examples:

- resident-to-unit relationship;
- owner-to-unit relationship;
- tenant-to-unit relationship;
- work-order assignment;
- facility eligibility;
- financial relationship.

The service owning the relevant domain data is responsible for validating the relationship.

---

## Identifiers

API-facing entity identifiers use UUIDs.

Example:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

Use a consistent UUID representation across all services.

Do not expose service-specific database identity conventions through public contracts.

---

## Date and time

Use ISO-8601.

### Date and time

```json
{
  "createdAt": "2026-09-30T12:30:00Z"
}
```

### Date only

```json
{
  "dateOfBirth": "2002-05-15"
}
```

### Time only

```json
{
  "startTime": "14:30:00"
}
```

Date/time fields must have a clearly defined semantic meaning.

Use UTC timestamps for API timestamps unless the endpoint contract explicitly requires another representation.

---

## JSON naming

JSON property names use `camelCase`.

Canonical:

```json
{
  "firstName": "John",
  "lastName": "Perera",
  "createdAt": "2026-09-30T12:30:00Z"
}
```

Avoid:

```json
{
  "first_name": "John",
  "created_at": "2026-09-30T12:30:00Z"
}
```

Enum values use uppercase identifiers.

Example:

```json
{
  "status": "ACTIVE"
}
```

---

## Standard success response

Successful JSON responses use the common envelope:

```json
{
  "success": true,
  "message": "Resident retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "firstName": "John",
    "lastName": "Perera"
  },
  "timestamp": "2026-09-30T12:30:00Z",
  "requestId": "7f83a9b2-..."
}
```

### Success fields

| Field | Rule |
|---|---|
| `success` | Always `true` |
| `message` | Human-readable operation result |
| `data` | Response payload |
| `timestamp` | Server response timestamp |
| `requestId` | Request trace identifier |

`data` may contain:

- a single resource;
- a collection;
- a primitive result;
- a domain-specific response object.

---

## Standard list response

For paginated list APIs:

```json
{
  "success": true,
  "message": "Residents retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "firstName": "John",
      "lastName": "Perera"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-30T12:30:00Z",
  "requestId": "7f83a9b2-..."
}
```

`pagination` is included for paginated collection responses.

---

## Pagination

Default pagination:

```text
page = 0
size = 20
```

Maximum recommended page size:

```text
size = 100
```

Example:

```http
GET /api/v1/residents?page=0&size=20
```

Pagination fields:

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

Services may define endpoint-specific maximum limits where required, but must document them.

---

## Filtering, searching and sorting

Use query parameters.

### Filtering

```http
GET /api/v1/residents?status=ACTIVE
```

### Multiple filters

```http
GET /api/v1/maintenance-requests?status=OPEN&priority=HIGH
```

### Search

```http
GET /api/v1/residents?search=perera
```

### Sorting

```http
GET /api/v1/residents?sort=lastName,asc
```

### Combined

```http
GET /api/v1/residents?search=perera&status=ACTIVE&page=0&size=20&sort=lastName,asc
```

Supported query parameters must be documented by each endpoint.

Services must not silently accept undocumented filtering or sorting parameters as part of a canonical contract.

---

## Standard error response

All services use the same basic error envelope:

```json
{
  "success": false,
  "message": "Resident not found",
  "error": {
    "code": "RESIDENT_NOT_FOUND",
    "details": null
  },
  "timestamp": "2026-09-30T12:30:00Z",
  "requestId": "7f83a9b2-..."
}
```

### Error fields

| Field | Rule |
|---|---|
| `success` | Always `false` |
| `message` | Human-readable explanation |
| `error.code` | Stable machine-readable error code |
| `error.details` | Optional structured details |
| `timestamp` | Server response timestamp |
| `requestId` | Request trace identifier |

Frontend and service-to-service consumers must use `error.code` for machine-readable handling rather than matching human-readable messages.

---

## Validation error response

Validation failures use:

```text
VALIDATION_ERROR
```

Example:

```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": [
      {
        "field": "email",
        "message": "Email must be valid"
      },
      {
        "field": "phone",
        "message": "Phone number is required"
      }
    ]
  },
  "timestamp": "2026-09-30T12:30:00Z",
  "requestId": "7f83a9b2-..."
}
```

Validation details must identify the relevant request field where possible.

---

## HTTP status codes

| Status | Meaning | Typical usage |
|---|---|---|
| `200 OK` | Successful request | GET, PUT, PATCH |
| `201 Created` | Resource created | POST |
| `202 Accepted` | Request accepted for later processing | Asynchronous operation |
| `204 No Content` | Successful operation without response body | DELETE |
| `400 Bad Request` | Invalid request | Malformed request |
| `401 Unauthorized` | Authentication failed or missing | Invalid/missing JWT |
| `403 Forbidden` | Authenticated but not authorized | Insufficient permission |
| `404 Not Found` | Resource does not exist | Unknown ID |
| `409 Conflict` | Business/state conflict | Duplicate/conflicting operation |
| `422 Unprocessable Entity` | Semantically invalid data | Domain validation where applicable |
| `429 Too Many Requests` | Rate limit exceeded | When rate limiting is active |
| `500 Internal Server Error` | Unexpected server error | Unhandled server failure |
| `503 Service Unavailable` | Dependency/service unavailable | Downstream service failure |

---

## Error code standard

Error codes must be:

- stable;
- machine-readable;
- uppercase;
- descriptive;
- documented;
- independent of human-readable messages.

Common codes include:

```text
VALIDATION_ERROR
RESOURCE_NOT_FOUND
DUPLICATE_RESOURCE
BUSINESS_RULE_VIOLATION
PERMISSION_DENIED
DEPENDENCY_UNAVAILABLE
INTERNAL_SERVER_ERROR
```

Domain-specific codes are defined by the owning service contract.

Examples:

```text
USER_NOT_FOUND
ACCOUNT_INACTIVE
RESIDENT_NOT_FOUND
UNIT_NOT_FOUND
LEASE_NOT_FOUND
INVOICE_NOT_FOUND
PAYMENT_EXCEEDS_BALANCE
MAINTENANCE_REQUEST_NOT_FOUND
BOOKING_CONFLICT
VISITOR_NOT_FOUND
```

---

## DELETE and historical records

Use `DELETE` only when actual deletion is appropriate.

For important business records where historical or audit information must be retained, prefer a domain-specific status or deactivation operation.

Example:

```http
PATCH /api/v1/residents/{residentId}/status
```

instead of physically deleting a historical resident record.

The service contract must define whether deletion is physical, logical, or prohibited.

---

## Inter-service communication

Services communicate through documented APIs.

Canonical:

```text
Service A
    |
    | REST API
    v
API Gateway
    |
    v
Service B
    |
    v
Service B Database
```

Forbidden:

```text
Service A
    |
    | Direct database access
    v
Service B Database
```

A service must never directly access another service's database.

Each service remains responsible for its own data.

Internal API paths use:

```text
/api/v1/internal/...
```

Internal calls use the project's JWT/service authentication architecture.

---

## Provider and consumer contract rule

Each cross-service API has one canonical provider.

The provider owns:

- endpoint;
- HTTP method;
- request schema;
- response schema;
- validation;
- authorization;
- error codes;
- business meaning;
- failure behavior.

Consumers must:

- use the provider's documented endpoint;
- send the provider's documented request;
- interpret the provider's documented response;
- handle documented error codes;
- not create a duplicate provider contract.

Consumers must not invent alternative response fields for the provider API.

---

## Dependency failure

If a downstream service is unavailable, do not return a successful response containing misleading empty data.

Incorrect:

```json
{
  "success": true,
  "data": {}
}
```

Correct:

```http
503 Service Unavailable
```

```json
{
  "success": false,
  "message": "Required service is temporarily unavailable",
  "error": {
    "code": "DEPENDENCY_UNAVAILABLE",
    "details": {
      "service": "identity-access-service"
    }
  },
  "timestamp": "2026-09-30T12:30:00Z",
  "requestId": "7f83a9b2-..."
}
```

A failed dependency must not cause local records to be incorrectly created, updated or deleted.

---

## Inter-service timeouts

Synchronous service calls must use bounded timeouts.

Recommended starting values:

```text
Connection timeout: 2 seconds
Read timeout:       5 seconds
```

Services must not wait indefinitely for another service.

If a dependency cannot respond within the configured limit, return an appropriate dependency failure such as:

```http
503 Service Unavailable
```

If configured timeout values change, they must be documented in the relevant deployment/configuration documentation.

---

## Cross-service request propagation

For synchronous calls, propagate:

```http
X-Request-ID: <same-request-id>
Authorization: Bearer <appropriate-token>
```

The JWT used for internal calls must follow the project JWT security standard.

A service must not forward an original unverified token.

---

## Health checks

Every backend service exposes:

```http
GET /actuator/health
```

The health endpoint must provide enough information for service/deployment tooling and troubleshooting.

Backend health should include application health and the relevant database connectivity check.

The API Gateway must provide a Gateway health mechanism capable of identifying unavailable backend dependencies according to the Gateway contract.

Health endpoints must not expose secrets, private keys, credentials or sensitive business data.

---

## Logging and traceability

Important operations must be logged appropriately.

Examples:

```text
LOGIN_SUCCESS
LOGIN_FAILURE
USER_CREATED
ROLE_CHANGED
RESIDENT_CREATED
RESIDENT_DEACTIVATED
LEASE_APPROVED
PAYMENT_RECORDED
MAINTENANCE_REQUEST_CREATED
WORK_ORDER_ASSIGNED
WORK_ORDER_COMPLETED
BOOKING_CREATED
ANNOUNCEMENT_PUBLISHED
```

Logs should include where appropriate:

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
passwords
JWT secrets
private keys
sensitive credentials
```

Logging must not expose sensitive business information unnecessarily.

---

## Security rules

All services must follow the project security standard.

### Passwords

Never store plain-text passwords.

### JWT

Never expose JWT private keys, signing secrets or sensitive token material.

### Validation

Validate request payloads before processing.

### Authorization

Check:

- authentication;
- canonical role;
- required business relationship/scope;
- endpoint-specific authorization.

### Errors

Do not expose:

- stack traces;
- database errors;
- internal class names;
- credentials;
- private keys;
- sensitive implementation details.

Use safe standardized error responses.

---

## Spring Boot validation

Use DTO validation for request payloads.

Example:

```java
@NotBlank
private String firstName;

@NotBlank
private String lastName;

@NotBlank
@Email
private String email;
```

Validation failures use:

```text
VALIDATION_ERROR
```

and the standard error envelope.

---

## Global exception handling

Each Spring Boot service must use centralized exception handling.

Recommended approach:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // validation errors
    // resource-not-found errors
    // business-rule errors
    // authorization errors
    // unexpected exceptions
}
```

Controllers must not independently invent incompatible error response structures.

---

## OpenAPI and Swagger

Every API endpoint must be documented using OpenAPI/Swagger.

Endpoint documentation must include:

- API ID;
- endpoint;
- HTTP method;
- description;
- authentication requirement;
- required roles;
- allowed callers;
- path parameters;
- query parameters;
- request body;
- validation rules;
- successful response;
- error responses;
- business error codes;
- dependent services;
- dependency failure behavior;
- request examples;
- response examples.

Standard Swagger endpoints:

```text
/swagger-ui.html
/v3/api-docs
```

Each service's OpenAPI document must match the implemented API contract.

---

## API testing

Every endpoint must have appropriate automated API tests.

Postman collections must cover important endpoint behavior.

Tests should cover, where applicable:

- successful request;
- validation failure;
- authentication failure;
- authorization failure;
- resource not found;
- duplicate/conflict conditions;
- business-rule violations;
- dependency failure;
- response schema;
- error schema;
- request ID propagation;
- cross-service integration.

Important workflows must include integration tests involving more than one service.

---

## API contract documentation

Each service contract must document every endpoint using a consistent structure:

```text
API ID
Service
Version
Type: public/internal
HTTP method
Endpoint
Purpose
Authentication
Required roles
Allowed callers
Path parameters
Query parameters
Request body
Validation rules
Success status
Success response
Error statuses
Error codes
Business rules
Dependencies
Dependency failure behavior
Request example
Response example
```

The service-specific contract files contain the actual endpoint definitions.

This document defines only the common format.

---

## API contract registry

The project maintains a central registry in:

```text
PROJECT-A-CROSS-SERVICE-API-REGISTRY.md
```

The registry records the provider/consumer relationship and cross-service API references.

The registry must not duplicate complete endpoint definitions from the service contracts.

---

## Contract change management

Before changing a shared API contract:

1. Identify affected services.
2. Update the provider contract.
3. Identify all consumers.
4. Review the change through the project integration process.
5. Update OpenAPI.
6. Update Postman collections.
7. Update automated tests.
8. Update frontend consumers where necessary.
9. Update the cross-service API registry.
10. Implement the coordinated change.

No team may silently change another service's contract.

---

## API design rules

Before an endpoint is accepted as part of the canonical contract, verify:

### URL

- Uses `/api/v1` or the explicitly defined version.
- Uses plural resource names where applicable.
- Uses kebab-case.
- Avoids unnecessary action names.
- Uses `/api/v1/internal/...` for internal APIs.

### Request

- Uses the correct HTTP method.
- Uses JSON where a JSON body is required.
- Validates request data.
- Documents authentication.
- Documents authorization.
- Documents path/query parameters.

### Response

- Uses the standard success envelope.
- Uses the standard error envelope.
- Uses the correct HTTP status.
- Uses stable error codes.
- Includes request ID.
- Includes timestamp.

### Security

- JWT requirements are documented.
- Role requirements are documented.
- Business relationship/scope requirements are documented.
- Sensitive data is protected.
- Errors are safe.

### Integration

- Dependencies are documented.
- No direct cross-service database access exists.
- Timeout behavior is defined.
- Dependency failure is handled.
- Request ID is propagated.

### Documentation and testing

- OpenAPI is updated.
- Swagger is verified.
- Postman request is added where applicable.
- Automated tests are added.
- Cross-service registry is updated where applicable.

---

## Standard implementation conventions

All services must keep the common API behavior in shared project conventions rather than creating local variations.

The implementation must consistently use:

```text
/api/v1
JSON
UUID
ISO-8601
camelCase
kebab-case
plural resources
JWT Bearer
X-Request-ID
standard success envelope
standard error envelope
stable error codes
bounded timeouts
503 dependency failures
OpenAPI
automated tests
```

Any exception to this standard must be explicitly defined in the relevant project-level or service-level contract.
