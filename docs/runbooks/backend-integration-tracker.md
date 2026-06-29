# Backend Integration Tracker

Use this tracker with `docs/api/frontend-backend-contract-v1.md`. The contract is the source of truth for request and response shapes.

## Status Legend

- `TODO`: Not started
- `IN_PROGRESS`: Backend work started
- `API_READY`: Endpoint returns the contracted shape
- `FRONTEND_VERIFIED`: Verified from the frontend screen
- `BLOCKED`: Needs a decision or dependency

## Working Rules

- Keep one branch per stream or feature group.
- Backend routes use base path `/api/v1`.
- Do not return JPA entities directly from controllers.
- Protected routes must derive user, owner, vendor, and role from JWT, not request body.
- If backend response shape needs to change, update `docs/api/frontend-backend-contract-v1.md` first.
- After each endpoint group is ready, verify it from the frontend immediately.

## Local Verification

Start PostgreSQL and MinIO:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/infra
docker compose up -d postgres minio minio-init
```

MinIO console: `http://localhost:9001`

Start backend:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/apps/backend
mvn spring-boot:run
```

Start frontend:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/apps/frontend
npm run dev -- -p 3000
```

Open:

```text
Frontend: http://localhost:3000
Backend health: http://localhost:8080/api/actuator/health
Swagger/OpenAPI: http://localhost:8080/swagger-ui.html
```

## Test Users

Seed or create one user for each role before integration QA.

| Role | Phone | Password | Status |
| --- | --- | --- | --- |
| `CUSTOMER` | `9876543210` | `Password123` | `TODO` |
| `HALL_OWNER` | `9876501234` | `Password123` | `TODO` |
| `VENDOR` | `9884012345` | `Password123` | `TODO` |
| `ADMIN` | `9000000001` | `Password123` | `TODO` |

## Stream 1: Auth, Public Discovery, Customer

Recommended first stream because it unlocks login, browsing, enquiry creation, and customer verification.

| Priority | Endpoint Group | Backend Status | Frontend Screen | Frontend Status | Notes |
| --- | --- | --- | --- | --- | --- |
| P0 | `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `POST /auth/refresh`, `POST /auth/logout` | `API_READY` | `/auth/login`, `/auth/register` | `FRONTEND_VERIFIED` | Use auth pattern runbook |
| P0 | `GET /public/halls`, `GET /public/halls/{hallId}` | `API_READY` | `/`, `/halls/{id}` | `TODO` | Public approved halls only; supports search/filter/sort/page |
| P0 | `GET /public/vendors`, `GET /public/vendors/{vendorId}` | `API_READY` | `/vendors`, `/vendors/{id}` | `TODO` | Public approved vendors only; supports search/filter/sort/page |
| P1 | `POST /public/enquiries` | `API_READY` | Hall detail enquiry form | `TODO` | Logged-in customer only; uses new `halls` table; accepts numeric hall id or frontend slug; creates `PENDING_OWNER_RESPONSE` enquiry |
| P1 | `GET /customer/enquiries`, `GET /customer/enquiries/{enquiryId}` | `API_READY` | `/customer?tab=enquiries` | `TODO` | Customer can only see own records |
| P1 | `GET /customer/bookings`, `GET /customer/bookings/{bookingId}` | `API_READY` | `/customer?tab=bookings` | `TODO` | Includes booking/payment status; Razorpay endpoints remain separate |
| P2 | `GET /customer/saved-halls`, `PUT /customer/saved-halls/{hallId}`, `DELETE /customer/saved-halls/{hallId}` | `API_READY` | `/customer?tab=saved` | `TODO` | CUSTOMER only; approved halls only; PUT/DELETE are idempotent |
| P2 | `GET /customer/review-eligibility`, `POST /customer/reviews`, `PUT /customer/reviews/{reviewId}` | `TODO` | `/customer?tab=reviews` | `TODO` | Only completed bookings are eligible |
| P3 | `POST /customer/bookings/{bookingId}/payments/advance-order`, `POST /customer/bookings/{bookingId}/payments/verify` | `TODO` | `/customer?tab=bookings` | `TODO` | Razorpay can be stubbed first |

## Stream 2: Owner Workspace

This stream owns hall-owner workflows and should verify every action with the owner role.

| Priority | Endpoint Group | Backend Status | Frontend Screen | Frontend Status | Notes |
| --- | --- | --- | --- | --- | --- |
| P0 | `GET /owner/halls`, `POST /owner/halls`, `GET /owner/halls/{hallId}`, `PUT /owner/halls/{hallId}` | `API_READY` | `/owner`, `/owner?tab=listing`, `/owner/onboarding` | `TODO` | Listing data belongs to logged-in owner; path `hallId` is enforced |
| P0 | `POST /owner/halls/{hallId}/submit` | `API_READY` | `/owner?tab=listing` | `TODO` | Returns `PENDING_APPROVAL` |
| P1 | `GET /owner/halls/{hallId}/enquiries`, `PATCH /owner/enquiries/{enquiryId}/status` | `API_READY` | `/owner?tab=enquiries` | `TODO` | Uses new `halls` table; confirm creates or activates booking; decline and completion transitions enforced |
| P1 | `GET /owner/halls/{hallId}/bookings`, `PATCH /owner/bookings/{bookingId}/status` | `API_READY` | `/owner?tab=bookings` | `TODO` | Uses new `halls` table; complete booking unlocks review eligibility; cancellation transition enforced |
| P1 | `GET /owner/halls/{hallId}/availability`, `POST /owner/halls/{hallId}/blocked-dates`, `DELETE /owner/halls/{hallId}/blocked-dates/{blockId}` | `TODO` | `/owner?tab=availability` | `TODO` | Prevent date/slot conflicts |
| P2 | `POST /owner/halls/{hallId}/media`, `PATCH /owner/halls/{hallId}/media/{mediaId}`, `DELETE /owner/halls/{hallId}/media/{mediaId}` | `TODO` | `/owner?tab=media` | `TODO` | Save metadata after shared upload presign |
| P2 | `GET /owner/halls/{hallId}/reviews` | `TODO` | `/owner?tab=reviews` | `TODO` | Verified customer reviews |
| P3 | `GET /owner/halls/{hallId}/reports/summary` | `TODO` | `/owner?tab=reports` | `TODO` | Aggregated numbers only |

## Stream 3: Vendor, Admin, Cross-Cutting

This stream can start with vendor APIs, then admin moderation. Keep admin mutations audited.

| Priority | Endpoint Group | Backend Status | Frontend Screen | Frontend Status | Notes |
| --- | --- | --- | --- | --- | --- |
| P0 | `GET /vendor/profile`, `PUT /vendor/profile`, `POST /vendor/profile/submit` | `API_READY` | `/vendor`, `/vendor/onboarding` | `TODO` | Vendor can only edit own profile |
| P1 | `GET /vendor/leads`, `GET /vendor/leads/{leadId}`, `PATCH /vendor/leads/{leadId}/status` | `TODO` | `/vendor?tab=leads` | `TODO` | Enforce valid lead transitions |
| P1 | `GET /vendor/packages`, `POST /vendor/packages`, `PUT /vendor/packages/{packageId}`, `DELETE /vendor/packages/{packageId}` | `API_READY` | `/vendor?tab=services` | `TODO` | Package ownership derives from JWT; package inclusions are stored separately |
| P2 | `GET /vendor/media`, `POST /vendor/media`, `PATCH /vendor/media/{mediaId}`, `DELETE /vendor/media/{mediaId}` | `API_READY` | `/vendor?tab=portfolio` | `TODO` | Portfolio metadata is owned by the logged-in vendor; cover updates normalize old cover |
| P2 | `POST /uploads/presign` | `API_READY` | `/vendor?tab=portfolio`, `/owner?tab=media` | `TODO` | Shared MinIO/S3 direct upload signing; supports `VENDOR_PORTFOLIO` and `OWNER_HALL_MEDIA` purpose |
| P2 | `GET /public/subscription-plans`, `GET /vendor/subscription`, `POST /vendor/subscription/orders`, `POST /vendor/subscription/verify` | `TODO` | `/vendor?tab=subscription` | `TODO` | Razorpay can be stubbed first |
| P2 | `GET /vendor/reports/summary` | `TODO` | `/vendor?tab=reports` | `TODO` | Aggregated lead funnel |
| P1 | `GET /admin/halls`, `PATCH /admin/halls/{hallId}/review` | `TODO` | `/admin?tab=venues` | `TODO` | Write audit event |
| P1 | `GET /admin/vendors`, `PATCH /admin/vendors/{vendorId}/review` | `API_READY` | `/admin?tab=vendors` | `TODO` | Stores reviewer metadata; full immutable audit stream remains separate |
| P2 | `GET /admin/users`, `PATCH /admin/users/{userId}/status` | `TODO` | `/admin?tab=users` | `TODO` | Protect admin/super-admin rules |
| P2 | `GET /admin/reviews`, `PATCH /admin/reviews/{reviewId}/moderation` | `TODO` | `/admin?tab=reviews` | `TODO` | Preserve verified review history |
| P2 | `GET /admin/enquiries`, `GET /admin/audit-events` | `TODO` | `/admin?tab=enquiries` | `TODO` | Support/admin visibility |
| P3 | `GET /admin/reports/summary` | `TODO` | `/admin?tab=reports` | `TODO` | Platform aggregates |
| P3 | `GET /notifications`, `PATCH /notifications/{notificationId}/read`, `PATCH /notifications/read-all` | `TODO` | Notification bell/activity tabs | `TODO` | Can use polling first |
| P3 | `POST /payments/razorpay/webhook` | `TODO` | Backend only | `TODO` | Idempotent webhook processing |

## Frontend Support Work

These are tasks we can handle while backend streams are moving.

| Task | Status | Notes |
| --- | --- | --- |
| Keep frontend API adapters aligned to the contract | `ONGOING` | Small field-name adapters only |
| Add missing loading/error states discovered during QA | `TODO` | Fix only when real backend exposes gaps |
| Create seed-data checklist for demos | `TODO` | Customer, owner, vendor, admin |
| Run module smoke tests after each backend PR | `TODO` | Use the frontend screens listed above |
| Update contract when a backend constraint changes | `ONGOING` | Contract first, code second |

## Handoff Checklist Per Endpoint Group

- Backend branch is up to date with latest main.
- Endpoint paths match the contract.
- Request and response DTOs are not entities.
- Authenticated routes derive identity from JWT.
- Empty state returns valid JSON shape.
- Validation errors return problem details.
- Unit/service tests are added.
- Frontend screen has been tested in API mode.
- Tracker status is updated.
