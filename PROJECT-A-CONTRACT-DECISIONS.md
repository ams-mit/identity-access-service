# Project A --- Contract Decisions

## Purpose

This document is the project-level source of truth for architectural and
contract decisions that affect multiple Project A services.

Project A is the Apartment Management System delivered jointly by Groups
1--4. The assignment requires the four teams to deliver one integrated
product rather than isolated services. Each team owns bounded business
domains and their microservices, while cross-team integration is
required through documented APIs and the shared API Gateway.

Detailed API rules belong in `PROJECT-A-GLOBAL-API-STANDARD.md`.
JWT/security rules belong in `PROJECT-A-JWT-SECURITY-STANDARD.md`. The
provider/consumer map belongs in
`PROJECT-A-CROSS-SERVICE-API-REGISTRY.md`.

## Canonical target-state rule

The contracts in this directory define the **target state** of Project
A.

The existing implementation in any repository is not the source of
truth.

When implementing a service against its contract, the implementation
team or AI coding agent must:

-   inspect the complete existing repository;
-   compare the implementation with the canonical contract;
-   remove obsolete APIs;
-   remove duplicate APIs;
-   remove conflicting APIs;
-   remove obsolete business logic;
-   modify incompatible implementation;
-   create missing implementation;
-   update database schema and migrations;
-   update security and authorization;
-   update tests;
-   update OpenAPI documentation;
-   update integration configuration;
-   verify Gateway routing and cross-service calls;
-   verify health checks and failure handling; and
-   remove temporary or obsolete artifacts after checking references.

Compatible functionality must **not** be preserved merely because it
already exists. If an existing endpoint, model, role, response, or
implementation conflicts with the canonical contract, it must be changed
or removed.

The result must be a clean implementation synchronized with the
canonical contracts.

## Project identity

  -----------------------------------------------------------------------
  Item                                Canonical decision
  ----------------------------------- -----------------------------------
  Project                             Project A --- Apartment Management
                                      System

  Institution                         University of Kelaniya

  Architecture                        Integrated microservice
                                      architecture

  Backend                             Spring Boot REST services

  Frontend                            Shared React + TypeScript
                                      application

  API entry point                     API Gateway

  Authentication                      JWT

  JWT signing                         RS256 asymmetric signing

  Service communication               REST through the API Gateway

  Database model                      One database/schema owned by each
                                      backend service

  API base version                    `/api/v1`

  API format                          JSON

  API documentation                   OpenAPI / Swagger

  Testing                             Unit, API, integration, system,
                                      security and appropriate acceptance
                                      testing

  Source control                      GitHub

  Work management                     Jira
  -----------------------------------------------------------------------

The assignment requires clear service boundaries, databases, APIs, API
Gateway behavior, secure authentication/authorization, testing,
integration and deployment evidence.

## Canonical service set

Project A contains eight backend business services and one API Gateway.

  -------------------------------------------------------------------------------
  Group                   Service                         Domain
  ----------------------- ------------------------------- -----------------------
  Group 1                 `identity-access-service`       Identity,
                                                          authentication, users,
                                                          roles and permissions

  Group 1                 `resident-management-service`   Resident, owner, tenant
                                                          and staff profiles and
                                                          relationships

  Group 2                 `property-unit-service`         Buildings, floors,
                                                          units, unit types and
                                                          ownership

  Group 2                 `lease-occupancy-service`       Leases, occupants and
                                                          occupancy

  Group 3                 `billing-payment-service`       Charges, invoices,
                                                          payments, receipts and
                                                          balances

  Group 3                 `utility-charge-service`        Utility charges

  Group 4                 `operations-service`            Maintenance, work
                                                          orders, assignments,
                                                          facilities and bookings

  Group 4                 `community-service`             Visitors, announcements
                                                          and notifications

  Joint                   `api-gateway`                   Routing, authentication
                                                          boundary and
                                                          Gateway-level request
                                                          handling
  -------------------------------------------------------------------------------

The API Gateway is jointly governed because it is an integration
component rather than a business-domain service.

## Service ownership boundaries

### Identity Access

`identity-access-service` owns:

-   User
-   Role
-   Permission
-   authentication
-   account status
-   user credentials
-   role assignment
-   User JWT issuance

Identity Access answers identity and access questions.

It does **not** own apartment/unit ownership, tenancy, occupancy or
other business-domain relationships.

### Resident Management

`resident-management-service` owns:

-   Resident profile
-   Owner profile
-   Tenant/resident profile
-   Staff profile
-   resident/owner/tenant/staff relationships
-   profile and relationship status required by its domain

Its public resource model uses:

``` text
/api/v1/residents
/api/v1/owners
/api/v1/tenants
/api/v1/staff
```

Identity Access remains the owner of `User`, `Role` and `Permission`.

Resident Management may reference a user identity, but it must not
duplicate Identity Access ownership of credentials, roles or
permissions.

Apartment/unit relationships that belong to property, lease or occupancy
are validated through the relevant Group 2 APIs.

### Property Unit

`property-unit-service` owns:

-   Building
-   Floor
-   Unit
-   UnitType
-   Ownership
-   unit status
-   unit availability information owned by the property domain

It is the provider for canonical unit/property validation required by
other services.

### Lease Occupancy

`lease-occupancy-service` owns:

-   Lease
-   Occupant
-   Occupancy
-   occupancy status
-   occupancy history
-   lease/occupancy conflict rules

It is the provider for canonical lease and occupancy validation.

### Billing Payment

`billing-payment-service` owns:

-   ChargeRule
-   Invoice
-   InvoiceLine
-   Payment
-   Receipt
-   Adjustment
-   invoice balances
-   arrears
-   payment status and financial records

Invoices preserve relevant line-item values used to calculate the
invoice and balance.

Simulated payments create auditable payment records and receipts.

### Utility Charge

`utility-charge-service` owns:

-   UtilityCharge
-   utility-specific charge data
-   utility charge status and calculations within its domain

Utility-specific financial records must not be duplicated as separate
billing resources in another service.

Where utility charges participate in a broader billing workflow,
services communicate through documented APIs rather than direct database
access.

### Operations

`operations-service` owns:

-   MaintenanceRequest
-   WorkOrder
-   Assignment
-   Facility
-   Booking

It owns the maintenance lifecycle and facility reservation lifecycle.

It validates resident/user and unit-related information through
documented cross-service APIs.

### Community

`community-service` owns:

-   Visitor
-   Announcement
-   Notification

Community owns notification records and notification workflow.

No other Project A service may create a second canonical Notification
resource.

Other services that need to trigger notifications must call the
documented Community notification API.

## Group 4 ownership decision

The assignment gives Group 4 two services --- `operations-service` and
`community-service` --- and lists maintenance, facilities, visitors and
communications in the Group 4 domain.

The Project A split is:

``` text
operations-service
    MaintenanceRequest
    WorkOrder
    Assignment
    Facility
    Booking

community-service
    Visitor
    Announcement
    Notification
```

This exact entity-to-service split is a project-level architectural
decision derived from the Group 4 scope and entity list; the assignment
does not prescribe the exact split.

## Notification ownership

`community-service` is the sole owner of the canonical Notification
resource.

Therefore:

-   Notification CRUD belongs to Community.
-   Notification persistence belongs to Community.
-   Notification lifecycle belongs to Community.
-   Other services may request notification creation through Community
    APIs.
-   Other services must not maintain a competing Notification resource.
-   Resident Management must not expose a second canonical notification
    API.
-   Operations must not expose a second canonical notification API.
-   Billing, Utility, Property and Lease services must use the Community
    contract when they need notification functionality.

## Canonical roles

The canonical role identifiers are:

``` text
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

These are the canonical values for new API contracts, JWT role claims,
authorization rules and persistent role references.

The case scope describes System Administrator, Apartment Manager, Owner,
Tenant / Resident, Finance Officer, Maintenance Coordinator, Technician
/ Service Staff, and Security Officer.

The project separates `TECHNICIAN` and `SERVICE_STAFF` as an
authorization decision so work-assignment and service-support
permissions can be expressed independently. This distinction is not
explicit in the case document and is therefore a project-level decision.

### Legacy role aliases

  Legacy value         Canonical value
  -------------------- ------------------------
  `ADMIN`              `SYSTEM_ADMINISTRATOR`
  `SYSTEM_ADMIN`       `SYSTEM_ADMINISTRATOR`
  `MANAGER`            `APARTMENT_MANAGER`
  `PROPERTY_MANAGER`   `APARTMENT_MANAGER`
  `TENANT`             `TENANT_RESIDENT`
  `RESIDENT`           `TENANT_RESIDENT`
  `OWN`                `OWNER`

Legacy aliases are migration/source aliases only. They must not be
introduced into new API contracts.

## Authorization model

Authentication and authorization are separate concerns.

Authorization must consider:

``` text
Authenticated identity
        +
Canonical role
        +
Required business/domain relationship
        +
Endpoint authorization rule
```

A valid JWT does not automatically prove:

-   ownership of a unit;
-   tenancy of a unit;
-   occupancy of a unit;
-   facility eligibility;
-   work-order assignment;
-   financial relationship to an invoice.

The service that owns the relevant domain data is responsible for
validating those relationships through its own data or documented
provider APIs.

## User JWT architecture

The canonical user flow is:

``` text
Frontend
    |
    | User JWT
    v
API Gateway
    |
    | verifies Identity Access signature
    | creates new Gateway JWT
    v
Backend Service
    |
    | verifies Gateway signature
    | authorizes user
    v
Business operation
```

Identity Access signs User JWTs.

The Gateway validates the User JWT and creates a new Gateway-signed JWT.

The original User JWT is not forwarded to backend services.

## Internal service authentication architecture

The canonical internal flow is:

``` text
Service A
    |
    | Service JWT
    v
API Gateway
    |
    | verifies Service A signature
    | checks service authorization
    | creates new Gateway JWT
    v
Service B
    |
    | verifies Gateway signature
    | authorizes Service A
    v
Business operation
```

Each backend service owns its own service private/public key pair.

A service signs its own Service JWT.

The Gateway stores trusted service public keys and verifies incoming
Service JWTs.

The Gateway then creates a new Gateway JWT and signs it with the Gateway
private key.

Service B does not need Service A's public key.

All internal calls use:

``` text
/api/v1/internal/...
```

Internal service calls go through the API Gateway according to the
project security architecture.

## JWT claim boundary

User JWTs contain:

``` text
sub
type
roles
iat
exp
```

Service JWTs contain:

``` text
sub
type
iat
exp
```

JWTs must not contain:

-   passwords or password hashes;
-   private keys or secrets;
-   sensitive profile data;
-   apartment/unit ownership;
-   occupancy information;
-   business-domain records;
-   a second permissions model;
-   unrelated domain data.

## Public and internal API boundary

Every cross-service API has one canonical provider.

The provider owns:

-   endpoint path;
-   HTTP method;
-   request schema;
-   response schema;
-   validation rules;
-   error codes;
-   authorization rules;
-   business meaning.

Consumers reference the provider contract rather than redefining it.

Public APIs use:

``` text
/api/v1/...
```

Internal APIs use:

``` text
/api/v1/internal/...
```

The complete provider/consumer relationship is maintained in
`PROJECT-A-CROSS-SERVICE-API-REGISTRY.md`.

## Data ownership and database isolation

Each backend service owns its own database/schema.

Canonical database names:

``` text
identity_access_db
resident_management_db
property_unit_db
lease_occupancy_db
billing_payment_db
utility_charge_db
operations_db
community_db
```

Each service reads and writes only its own database.

A service must never directly query another service's database.

Cross-service data is obtained through documented APIs or another
explicitly approved integration mechanism.

## Resource ownership rule

Every canonical business resource has exactly one owner.

The owning service is responsible for:

-   persistence;
-   business rules;
-   validation;
-   lifecycle;
-   public resource APIs;
-   internal provider APIs where required.

Another service may hold references such as UUIDs or external
identifiers, but must not become a second owner.

## Cross-service relationship validation

Cross-service workflows validate relationships through the service that
owns the relationship:

``` text
User identity
    -> Identity Access

Resident/profile relationship
    -> Resident Management

Unit/property relationship
    -> Property Unit

Lease/occupancy relationship
    -> Lease Occupancy

Financial status
    -> Billing Payment

Utility charge
    -> Utility Charge

Maintenance/facility booking
    -> Operations

Visitor/announcement/notification
    -> Community
```

No service may infer another service's authoritative business state from
stale local assumptions when a provider API is required.

## Dependency failure

A service must not fabricate successful business state when a required
dependency is unavailable.

If a required downstream dependency cannot be reached or does not
provide the required authoritative result:

-   do not create false success;
-   do not silently assume valid data;
-   do not write inconsistent dependent state;
-   return the standardized dependency failure.

The global API standard defines the canonical dependency failure
response and timeout behavior.

## API versioning

The canonical API version is:

``` text
/api/v1
```

Breaking changes require a new API version or an explicitly approved
migration strategy.

A service must not silently change endpoint paths, HTTP methods, request
fields, response fields, field meanings, enum values, authorization
requirements, error codes or business semantics.

Contract changes must be reflected in the service contract, OpenAPI, API
registry, tests, integration configuration and relevant change records.

## Contract change rule

Any cross-service contract change must identify:

-   affected provider;
-   affected consumers;
-   reason;
-   request/response impact;
-   authorization impact;
-   database impact where applicable;
-   migration requirements;
-   test impact;
-   OpenAPI impact;
-   integration impact.

Breaking changes require cross-team agreement before implementation.

No team may silently change another team's API contract.

## API Gateway responsibility boundary

The API Gateway is responsible for platform-level request handling such
as:

-   route registration;
-   request forwarding;
-   JWT validation at the Gateway boundary;
-   Gateway JWT creation;
-   service trust enforcement;
-   request ID propagation;
-   consistent downstream failure handling;
-   configured CORS/rate limiting where applicable;
-   Gateway-level health aggregation;
-   security-related Gateway concerns.

The Gateway does not own business-domain data or business-domain
resources.

Business rules remain inside the owning service.

## No direct database integration

Forbidden:

``` text
Service A
    |
    +----> Service B database
```

Canonical:

``` text
Service A
    |
    v
API Gateway
    |
    v
Service B
```

This applies to both application workflows and internal
service-to-service communication.

## Shared technical conventions

The global API standard defines the detailed conventions. Project-level
conventions are:

-   Java 21;
-   Spring Boot;
-   Maven;
-   common Maven group ID `kln.ams`;
-   REST/JSON APIs;
-   UUID identifiers;
-   ISO-8601 date/time values;
-   JSON `camelCase`;
-   kebab-case URL paths;
-   plural resource names;
-   `/api/v1` versioning;
-   OpenAPI/Swagger;
-   standardized success and error envelopes;
-   `X-Request-ID` request correlation;
-   standardized HTTP status handling;
-   standardized validation and exception handling.

Each service follows the common standard rather than inventing a local
API style.

## Canonical service package names

``` text
kln.ams.identityaccess
kln.ams.residentmanagement
kln.ams.propertyunit
kln.ams.leaseoccupancy
kln.ams.billingpayment
kln.ams.utilitycharge
kln.ams.operations
kln.ams.community
kln.ams.apigateway
```

## Canonical repository names

``` text
identity-access-service
resident-management-service
property-unit-service
lease-occupancy-service
billing-payment-service
utility-charge-service
operations-service
community-service
api-gateway
```

The shared frontend is maintained separately and is not a backend
service contract.

## Canonical API ownership

  Resource / operation   Canonical owner
  ---------------------- ---------------------
  Authentication         Identity Access
  Users                  Identity Access
  Roles                  Identity Access
  Permissions            Identity Access
  Residents              Resident Management
  Owners                 Resident Management
  Tenants                Resident Management
  Staff profiles         Resident Management
  Buildings              Property Unit
  Floors                 Property Unit
  Units                  Property Unit
  Unit types             Property Unit
  Ownership records      Property Unit
  Leases                 Lease Occupancy
  Occupants              Lease Occupancy
  Occupancy              Lease Occupancy
  Charges                Billing Payment
  Invoices               Billing Payment
  Invoice lines          Billing Payment
  Payments               Billing Payment
  Receipts               Billing Payment
  Adjustments            Billing Payment
  Utility charges        Utility Charge
  Maintenance requests   Operations
  Work orders            Operations
  Assignments            Operations
  Facilities             Operations
  Facility bookings      Operations
  Visitors               Community
  Announcements          Community
  Notifications          Community

## Workflow ownership principle

Major Project A workflows are implemented through service APIs rather
than duplicated business logic.

### Resident onboarding

Identity Access establishes the account and role context.

Resident Management establishes the appropriate
resident/owner/tenant/staff profile and relationship.

Property/Lease services provide property or occupancy validation where
required.

### Monthly billing

Billing Payment owns invoice and balance creation.

Property, Lease, Resident and Utility services provide authoritative
information through documented contracts where required.

### Payment recording

Billing Payment owns the payment record, receipt and resulting financial
state.

### Maintenance request

Operations owns the maintenance request, work order, assignment and
lifecycle.

Identity/Resident and Property/Occupancy services provide requester and
unit validation.

### Facility reservation

Operations owns facilities and bookings.

Operations validates eligibility and time conflicts using authoritative
information from relevant domain services.

Billing balance information may be consumed only where a documented
booking rule requires it.

### Visitor handling

Community owns visitor records and visitor workflow.

Community obtains required resident/unit validation through documented
APIs rather than direct database access.

## API and security consistency rule

Every service uses the same project-wide:

-   API envelope;
-   error envelope;
-   request ID convention;
-   authentication model;
-   JWT type model;
-   role naming;
-   internal API namespace;
-   HTTP status conventions;
-   OpenAPI conventions;
-   validation conventions;
-   service-to-service authentication architecture.

A service must not introduce an independent authentication scheme or
incompatible API format without an explicit project-level contract
decision.

## Existing-code cleanup policy

When a repository is aligned with this contract, perform a deliberate
cleanup pass.

Remove:

-   obsolete endpoints;
-   duplicate endpoints;
-   conflicting endpoint paths;
-   legacy role names from active contracts;
-   duplicate notification ownership;
-   direct cross-service database access;
-   obsolete DTOs;
-   obsolete controllers;
-   obsolete service methods;
-   obsolete repository code;
-   unused migrations;
-   obsolete security configuration;
-   temporary test/demo endpoints;
-   stale OpenAPI definitions;
-   stale documentation that contradicts the canonical contract.

Do not leave two implementations active merely to preserve backward
compatibility unless a versioned migration is explicitly part of the
approved contract.

## AI implementation instruction

An AI coding agent working on any Project A repository must treat the
applicable contract documents as the target state.

The implementation sequence is:

``` text
Inspect repository
        ↓
Understand current architecture
        ↓
Compare implementation with canonical contract
        ↓
Identify obsolete / duplicate / conflicting functionality
        ↓
Plan required transformation
        ↓
Remove unwanted implementation
        ↓
Modify incompatible implementation
        ↓
Create missing implementation
        ↓
Update database and migrations
        ↓
Update security and authorization
        ↓
Update tests
        ↓
Update OpenAPI
        ↓
Compile
        ↓
Run tests
        ↓
Verify API behavior
        ↓
Verify cross-service integration
        ↓
Verify Gateway behavior
        ↓
Verify database ownership
        ↓
Verify security
        ↓
Verify error handling
        ↓
Verify health and Swagger
        ↓
Remove obsolete artifacts
        ↓
Final clean verification
```

The agent must not assume that existing code is correct merely because
it compiles or because an endpoint already exists.

## Forbidden architectural patterns

The following are prohibited unless this contract is explicitly changed:

-   direct access to another service's database;
-   duplicate ownership of a business resource;
-   duplicate Notification resources;
-   service-specific JWT formats;
-   independent authentication schemes;
-   forwarding original unverified JWTs to backends;
-   trusting JWT claims before signature verification;
-   placing apartment ownership or occupancy data in JWT claims;
-   bypassing the API Gateway for internal service communication;
-   silently changing another service's API;
-   duplicate endpoints with different contracts;
-   legacy role values in new contracts;
-   business logic inside the API Gateway;
-   fabricated success when a required dependency is unavailable;
-   undocumented cross-service APIs;
-   undocumented public endpoints;
-   exposing secrets or private keys in source control or logs.

## Decision authority

Project-wide contract decisions affecting multiple groups must be agreed
through the project's cross-team coordination process and reflected in
this document.

Service-local implementation details may be decided by the owning team
when they do not violate:

-   this document;
-   the global API standard;
-   the JWT/security standard;
-   the cross-service API registry;
-   another service's canonical provider contract;
-   the assignment's service boundaries.

When two documents conflict, the conflict must be resolved explicitly
rather than silently choosing one implementation.

