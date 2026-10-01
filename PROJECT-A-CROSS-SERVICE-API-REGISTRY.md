# Project A — Cross-Service API Registry

## Purpose

This registry defines the canonical relationships between Project A services.

It records:

- which service owns each cross-service API;
- which services consume it;
- the canonical internal endpoint;
- the purpose of the interaction;
- the allowed caller type;
- the major workflows that depend on the interaction.

The complete request/response contract for each endpoint belongs to the provider service contract.

Consumers must use the provider contract and must not redefine it.

---

## Service identifiers

| Service | Group | Domain |
|---|---|---|
| `identity-access-service` | Group 1 | Identity and access |
| `resident-management-service` | Group 1 | Resident and profile management |
| `property-unit-service` | Group 2 | Property and units |
| `lease-occupancy-service` | Group 2 | Leases and occupancy |
| `billing-payment-service` | Group 3 | Billing and payments |
| `utility-charge-service` | Group 3 | Utility charges |
| `operations-service` | Group 4 | Maintenance and facilities |
| `community-service` | Group 4 | Visitors, announcements and notifications |
| `api-gateway` | Joint | Gateway and integration |

---

## Cross-service communication rule

All service-to-service communication uses documented REST APIs.

Canonical flow:

```text
Service A
    |
    | Service JWT
    v
API Gateway
    |
    | Gateway JWT
    v
Service B
```

Internal endpoints use:

```text
/api/v1/internal/...
```

Direct database access between services is prohibited.

```text
Service A
    |
    X----> Service B Database
```

The provider service remains the sole owner of its data and business rules.

---

## Provider/consumer rule

For every cross-service API:

```text
One provider
Many consumers
One canonical contract
```

The provider owns:

- endpoint;
- HTTP method;
- request schema;
- response schema;
- validation;
- authorization;
- error codes;
- business meaning;
- dependency behavior.

Consumers must not create alternate versions of the same provider contract.

---

# Identity and Resident APIs

## Identity Access — User validation

| Field | Value |
|---|---|
| API ID | `IAM-INT-001` |
| Provider | `identity-access-service` |
| Consumers | `resident-management-service`, `property-unit-service`, `lease-occupancy-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/users/{userId}/validate` |
| Caller | Registered backend service |
| Purpose | Validate that a user exists and is eligible for the requested identity-level operation |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Identity Access |

The response must contain only identity/access information defined by the Identity Access contract.

Business-domain relationships such as apartment ownership or occupancy are not established by this API.

---

## Identity Access — Account status

| Field | Value |
|---|---|
| API ID | `IAM-INT-002` |
| Provider | `identity-access-service` |
| Consumers | `resident-management-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/users/{userId}/status` |
| Caller | Registered backend service |
| Purpose | Validate current account status when a workflow requires current identity/account state |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Identity Access |

---

## Resident Management — Profile validation

| Field | Value |
|---|---|
| API ID | `RES-INT-001` |
| Provider | `resident-management-service` |
| Consumers | `property-unit-service`, `lease-occupancy-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/residents/{residentId}/validate` |
| Caller | Registered backend service |
| Purpose | Validate a resident/profile relationship required by another service |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Resident Management |

This API validates Resident Management-owned profile/relationship information only.

---

## Resident Management — User relationship validation

| Field | Value |
|---|---|
| API ID | `RES-INT-002` |
| Provider | `resident-management-service` |
| Consumers | `property-unit-service`, `lease-occupancy-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/users/{userId}/relationships` |
| Caller | Registered backend service |
| Purpose | Obtain the user-domain relationships required to validate a business operation |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Resident Management |

The response must not duplicate Identity Access role/permission ownership.

---

# Property and Unit APIs

## Property Unit — Unit existence

| Field | Value |
|---|---|
| API ID | `PROP-INT-001` |
| Provider | `property-unit-service` |
| Consumers | `resident-management-service`, `lease-occupancy-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/exists` |
| Caller | Registered backend service |
| Purpose | Confirm that a unit exists |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Property Unit |

---

## Property Unit — Unit validation

| Field | Value |
|---|---|
| API ID | `PROP-INT-002` |
| Provider | `property-unit-service` |
| Consumers | `resident-management-service`, `lease-occupancy-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/validate` |
| Caller | Registered backend service |
| Purpose | Validate unit status and property-domain conditions required by another service |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Property Unit |

---

## Property Unit — Ownership validation

| Field | Value |
|---|---|
| API ID | `PROP-INT-003` |
| Provider | `property-unit-service` |
| Consumers | `resident-management-service`, `lease-occupancy-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/ownership` |
| Caller | Registered backend service |
| Purpose | Validate the authoritative ownership relationship for a unit |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Property Unit |

---

## Property Unit — Unit status

| Field | Value |
|---|---|
| API ID | `PROP-INT-004` |
| Provider | `property-unit-service` |
| Consumers | `lease-occupancy-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/status` |
| Caller | Registered backend service |
| Purpose | Obtain authoritative unit status |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Property Unit |

---

# Lease and Occupancy APIs

## Lease Occupancy — Occupancy validation

| Field | Value |
|---|---|
| API ID | `LEASE-INT-001` |
| Provider | `lease-occupancy-service` |
| Consumers | `resident-management-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/occupancy` |
| Caller | Registered backend service |
| Purpose | Obtain authoritative current occupancy information |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Lease Occupancy |

---

## Lease Occupancy — Occupant validation

| Field | Value |
|---|---|
| API ID | `LEASE-INT-002` |
| Provider | `lease-occupancy-service` |
| Consumers | `resident-management-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/occupants` |
| Caller | Registered backend service |
| Purpose | Validate occupants associated with a unit |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Lease Occupancy |

---

## Lease Occupancy — User occupancy relationship

| Field | Value |
|---|---|
| API ID | `LEASE-INT-003` |
| Provider | `lease-occupancy-service` |
| Consumers | `resident-management-service`, `billing-payment-service`, `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/users/{userId}/occupancy` |
| Caller | Registered backend service |
| Purpose | Validate whether a user has an authoritative active occupancy relationship |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Lease Occupancy |

---

# Billing and Payment APIs

## Billing Payment — Invoice balance

| Field | Value |
|---|---|
| API ID | `BILL-INT-001` |
| Provider | `billing-payment-service` |
| Consumers | `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/balance` |
| Caller | Registered backend service |
| Purpose | Obtain authoritative outstanding balance when a documented workflow requires it |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Billing Payment |

A consumer must not calculate or maintain a competing authoritative balance.

---

## Billing Payment — User financial status

| Field | Value |
|---|---|
| API ID | `BILL-INT-002` |
| Provider | `billing-payment-service` |
| Consumers | `operations-service`, `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/users/{userId}/balance-status` |
| Caller | Registered backend service |
| Purpose | Obtain authoritative financial status where a business rule requires user-level financial validation |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Billing Payment |

---

## Billing Payment — Payment status

| Field | Value |
|---|---|
| API ID | `BILL-INT-003` |
| Provider | `billing-payment-service` |
| Consumers | `community-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/payments/{paymentId}/status` |
| Caller | Registered backend service |
| Purpose | Obtain authoritative payment status for a workflow that references a payment |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Billing Payment |

---

# Utility Charge APIs

## Utility Charge — Unit charge summary

| Field | Value |
|---|---|
| API ID | `UTIL-INT-001` |
| Provider | `utility-charge-service` |
| Consumers | `billing-payment-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/units/{unitId}/charges` |
| Caller | Registered backend service |
| Purpose | Provide utility charge information required by billing |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Utility Charge |

Billing Payment must not directly access the Utility database.

---

## Utility Charge — Charge validation

| Field | Value |
|---|---|
| API ID | `UTIL-INT-002` |
| Provider | `utility-charge-service` |
| Consumers | `billing-payment-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/utility-charges/{chargeId}/validate` |
| Caller | Registered backend service |
| Purpose | Validate a utility charge referenced by a billing operation |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Utility Charge |

---

# Operations APIs

## Operations — Maintenance requester validation

Operations consumes identity/resident and unit information rather than exposing another service's data as its own.

Required provider relationships:

```text
operations-service
    |
    +--> resident-management-service
    |
    +--> property-unit-service
    |
    +--> lease-occupancy-service
```

These calls are used when a maintenance request requires:

- authenticated requester validation;
- resident/profile validation;
- valid unit validation;
- active occupancy/relationship validation.

The authoritative APIs are:

```text
RES-INT-001
RES-INT-002
PROP-INT-001
PROP-INT-002
LEASE-INT-001
LEASE-INT-003
```

Operations must not create duplicate user, resident, unit or occupancy resources.

---

# Community APIs

## Community — Notification creation

| Field | Value |
|---|---|
| API ID | `COMM-INT-001` |
| Provider | `community-service` |
| Consumers | `resident-management-service`, `billing-payment-service`, `utility-charge-service`, `operations-service`, `lease-occupancy-service`, `property-unit-service` |
| Method | `POST` |
| Endpoint | `/api/v1/internal/notifications` |
| Caller | Registered backend service |
| Purpose | Request creation of an in-application notification |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Community |

Community is the sole owner of Notification.

Consumers request notification creation through this API rather than storing notification records themselves.

---

## Community — Notification status

| Field | Value |
|---|---|
| API ID | `COMM-INT-002` |
| Provider | `community-service` |
| Consumers | `resident-management-service`, `billing-payment-service`, `operations-service` |
| Method | `GET` |
| Endpoint | `/api/v1/internal/notifications/{notificationId}/status` |
| Caller | Registered backend service |
| Purpose | Obtain notification delivery/read status when a workflow requires it |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Community |

---

## Community — Resident notification request

| Field | Value |
|---|---|
| API ID | `COMM-INT-003` |
| Provider | `community-service` |
| Consumers | `operations-service`, `billing-payment-service`, `lease-occupancy-service` |
| Method | `POST` |
| Endpoint | `/api/v1/internal/notifications/resident` |
| Caller | Registered backend service |
| Purpose | Request an in-application notification targeted to a resident/user |
| Authentication | Service JWT → Gateway JWT |
| Response owner | Community |

The provider owns notification persistence and lifecycle.

---

# Cross-service dependency matrix

| Consumer | Provider | Dependency |
|---|---|---|
| `resident-management-service` | `identity-access-service` | User/account validation |
| `resident-management-service` | `property-unit-service` | Unit/property validation |
| `resident-management-service` | `lease-occupancy-service` | Occupancy validation |
| `property-unit-service` | `identity-access-service` | User/relationship validation |
| `lease-occupancy-service` | `identity-access-service` | User/relationship validation |
| `lease-occupancy-service` | `property-unit-service` | Unit validation |
| `billing-payment-service` | `identity-access-service` | User/role validation |
| `billing-payment-service` | `resident-management-service` | Resident/profile relationship |
| `billing-payment-service` | `property-unit-service` | Unit validation |
| `billing-payment-service` | `lease-occupancy-service` | Occupancy/eligibility |
| `billing-payment-service` | `utility-charge-service` | Utility charges |
| `utility-charge-service` | `property-unit-service` | Unit validation where required |
| `utility-charge-service` | `lease-occupancy-service` | Occupancy/eligibility where required |
| `operations-service` | `identity-access-service` | User/account validation |
| `operations-service` | `resident-management-service` | Resident relationship validation |
| `operations-service` | `property-unit-service` | Unit/status validation |
| `operations-service` | `lease-occupancy-service` | Occupancy validation |
| `operations-service` | `billing-payment-service` | Balance status only when a documented booking rule requires it |
| `community-service` | `identity-access-service` | User/account validation |
| `community-service` | `resident-management-service` | Resident relationship validation |
| `community-service` | `property-unit-service` | Unit validation |
| `community-service` | `lease-occupancy-service` | Occupancy validation |
| `community-service` | `billing-payment-service` | Financial status only when a documented workflow requires it |
| `operations-service` | `community-service` | Notification request |
| `billing-payment-service` | `community-service` | Notification request |
| `lease-occupancy-service` | `community-service` | Notification request |
| `resident-management-service` | `community-service` | Notification request where required |

---

# Service dependency direction

```text
                    +-----------------------+
                    | identity-access       |
                    +-----------+-----------+
                                |
              +-----------------+------------------+
              |                 |                  |
              v                 v                  v
     resident-management   property-unit   lease-occupancy
              |                 |                  |
              +-----------------+------------------+
                                |
                                v
                       billing-payment
                                |
                                v
                       utility-charge


resident-management
        |
        +--------------------+
        |                    |
        v                    v
property-unit        lease-occupancy
        |                    |
        +---------+----------+
                  |
                  v
           operations
                  |
                  v
             community
```

The diagram represents dependency relationships, not database ownership.

---

# Workflow: resident onboarding

```text
Frontend
   |
   v
API Gateway
   |
   v
Identity Access
   |
   | create/activate user
   v
Resident Management
   |
   +----> Property Unit
   |       validate unit
   |
   +----> Lease Occupancy
           validate occupancy
   |
   v
Resident relationship established
```

Primary cross-service dependencies:

```text
Identity Access
Property Unit
Lease Occupancy
```

Resident Management owns the resident profile.

---

# Workflow: monthly billing

```text
Billing Payment
      |
      +----> Property Unit
      |       validate eligible unit
      |
      +----> Lease Occupancy
      |       validate occupancy
      |
      +----> Resident Management
      |       validate relevant profile/relationship
      |
      +----> Utility Charge
              obtain utility charges
      |
      v
Invoice created
```

Billing Payment owns the invoice and resulting financial state.

---

# Workflow: payment recording

```text
Finance user
    |
    v
API Gateway
    |
    v
Billing Payment
    |
    v
Payment recorded
    |
    +--> Invoice balance updated
    |
    +--> Receipt created
    |
    +--> Community notification requested
```

Billing Payment remains the authoritative provider for payment and balance state.

---

# Workflow: maintenance request

```text
Resident
   |
   v
API Gateway
   |
   v
Operations
   |
   +----> Identity Access
   |       validate user/account
   |
   +----> Resident Management
   |       validate resident relationship
   |
   +----> Property Unit
   |       validate unit
   |
   +----> Lease Occupancy
   |       validate active occupancy
   |
   v
MaintenanceRequest
   |
   v
WorkOrder
   |
   v
Assignment
   |
   v
Community
   |
   v
Notification
```

Operations owns the maintenance lifecycle.

---

# Workflow: facility reservation

```text
Resident
   |
   v
API Gateway
   |
   v
Operations
   |
   +----> Identity Access
   |       validate user/account
   |
   +----> Resident Management
   |       validate relationship
   |
   +----> Property Unit
   |       validate unit/status
   |
   +----> Lease Occupancy
   |       validate occupancy
   |
   +----> Billing Payment
   |       balance validation only when
   |       a documented booking rule requires it
   |
   v
Booking created
   |
   v
Community notification
```

Operations owns the Facility and Booking resources.

---

# Workflow: visitor handling

```text
Security / Manager
   |
   v
API Gateway
   |
   v
Community
   |
   +----> Identity Access
   |       validate authorized user
   |
   +----> Resident Management
   |       validate resident relationship
   |
   +----> Property Unit
   |       validate unit
   |
   +----> Lease Occupancy
           validate occupancy
   |
   v
Visitor record
```

Community owns the Visitor resource.

---

# Notification workflow

```text
Business Service
      |
      | POST /api/v1/internal/notifications
      v
API Gateway
      |
      v
Community
      |
      +--> validate notification request
      |
      +--> persist Notification
      |
      +--> expose notification state
      v
In-application notification
```

No other service stores the canonical Notification resource.

---

# Dependency failure rule

A consumer must not fabricate a successful result when a required provider is unavailable.

If a required dependency fails:

```text
Provider unavailable
       |
       v
Consumer cannot obtain authoritative result
       |
       v
Do not create misleading local state
       |
       v
Return standardized dependency failure
```

Canonical error:

```text
DEPENDENCY_UNAVAILABLE
```

Canonical HTTP status:

```http
503 Service Unavailable
```

The consumer must not substitute stale or invented data unless the service contract explicitly defines such behavior.

---

# Timeout rule

Synchronous cross-service calls use bounded timeouts.

Recommended defaults:

```text
Connection timeout: 2 seconds
Read timeout:       5 seconds
```

A service must not wait indefinitely for another service.

---

# Authentication for registry APIs

All internal APIs registered in this document use the Project A service authentication architecture:

```text
Calling Service
    |
    | Service JWT
    v
API Gateway
    |
    | verifies caller
    | creates Gateway JWT
    v
Provider Service
```

The provider verifies the Gateway JWT and authorizes the calling service.

---

# Allowed caller rule

Each provider endpoint must explicitly define its allowed calling services in the provider service contract.

A valid Service JWT alone does not grant permission to every internal endpoint.

Example:

```text
Authenticated:
resident-management-service

Endpoint:
POST /api/v1/internal/notifications

Provider:
community-service

Policy:
resident-management-service is allowed

Result:
ALLOW
```

If the service is authenticated but not allowed:

```http
403 Forbidden
```

---

# API ownership rules

The following ownership is fixed:

| Resource | Owner |
|---|---|
| User | `identity-access-service` |
| Role | `identity-access-service` |
| Permission | `identity-access-service` |
| Resident | `resident-management-service` |
| Owner | `resident-management-service` |
| Tenant | `resident-management-service` |
| Staff profile | `resident-management-service` |
| Building | `property-unit-service` |
| Floor | `property-unit-service` |
| Unit | `property-unit-service` |
| UnitType | `property-unit-service` |
| Ownership | `property-unit-service` |
| Lease | `lease-occupancy-service` |
| Occupant | `lease-occupancy-service` |
| Occupancy | `lease-occupancy-service` |
| ChargeRule | `billing-payment-service` |
| Invoice | `billing-payment-service` |
| InvoiceLine | `billing-payment-service` |
| Payment | `billing-payment-service` |
| Receipt | `billing-payment-service` |
| Adjustment | `billing-payment-service` |
| UtilityCharge | `utility-charge-service` |
| MaintenanceRequest | `operations-service` |
| WorkOrder | `operations-service` |
| Assignment | `operations-service` |
| Facility | `operations-service` |
| Booking | `operations-service` |
| Visitor | `community-service` |
| Announcement | `community-service` |
| Notification | `community-service` |

---

# Forbidden cross-service patterns

Do not:

- access another service's database;
- create a duplicate provider for an existing API;
- duplicate another service's resource;
- duplicate Notification;
- copy another service's authoritative business state into a competing local resource;
- bypass the API Gateway for internal calls;
- use an undocumented internal endpoint;
- silently change a provider response;
- create service-specific alternate response formats;
- use a Service JWT to bypass endpoint authorization;
- return fabricated success when a required provider is unavailable.

---

# Contract change rule

Before changing any registered cross-service API:

1. Identify the provider.
2. Identify every consumer.
3. Update the provider service contract.
4. Update the affected consumer contracts.
5. Update OpenAPI.
6. Update automated tests.
7. Update Postman collections where applicable.
8. Update Gateway routing if the path changes.
9. Update this registry.
10. Complete cross-service integration verification.

Breaking changes must not be introduced silently.

---

# Registry maintenance rule

This registry records **cross-service contracts and relationships**.

It does not replace:

```text
PROJECT-A-CONTRACT-DECISIONS.md
PROJECT-A-GLOBAL-API-STANDARD.md
PROJECT-A-JWT-SECURITY-STANDARD.md
<service-specific contract>.md
```

Service-specific files remain the authoritative location for complete endpoint definitions.

If a cross-service endpoint is changed, the provider service contract and this registry must remain synchronized.
