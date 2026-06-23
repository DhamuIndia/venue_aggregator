# Backend Integration: Next Two Modules

Use the auth integration as the reference pattern before starting these modules.

## Recommendation

Assign two separate vertical slices:

- Public Hall Discovery and Hall Detail
- Enquiries and Customer Enquiry Tracking

These are the best next modules because they are focused, independent, and cover the full integration pattern: controller, DTO, service, repository, validation, API contract, frontend wiring, and tests.

## Shared Rules

- Base URL is `/api/v1`.
- Controllers should be thin.
- Services should contain business rules.
- DTOs should not expose entity classes directly.
- Use `@Valid` request DTOs.
- Protected routes must read user identity from JWT principal.
- Return `application/problem+json` errors through the existing exception handler.
- Do not change frontend UI design unless needed for API integration.
- Update `docs/api/frontend-backend-contract-v1.md` if the API shape changes.

## Run Locally

Prerequisite: Docker Desktop must be running.

Start PostgreSQL:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/infra
docker compose up -d postgres
docker compose ps
```

Start backend:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/apps/backend
mvn spring-boot:run
```

Check backend:

```bash
curl http://localhost:8080/api/actuator/health
```

Start frontend in another terminal:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/apps/frontend
cp .env.example .env.local
npm install
npm run dev -- -p 3001
```

Open:

```text
http://localhost:3001
http://localhost:8080/api/docs
```

Stop PostgreSQL:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/infra
docker compose down
```

## Public Hall Discovery and Hall Detail

### Goal

Replace mock hall data in the frontend with backend APIs for public browsing.

### Backend APIs

Implement:

- `GET /api/v1/public/halls`
- `GET /api/v1/public/halls/{hallId}`
- `GET /api/v1/public/halls/{hallId}/availability?from=YYYY-MM-DD&to=YYYY-MM-DD`

### Frontend Areas

- `apps/frontend/features/halls/mock-data.ts`
- `apps/frontend/components/halls/HallDiscovery.tsx`
- `apps/frontend/app/(public)/halls/[id]/page.tsx`

### Acceptance Criteria

- Hall listing page loads from backend.
- Hall detail page loads from backend by ID or slug.
- Search/filter parameters are accepted by the backend.
- Only approved/active halls are visible publicly.
- Frontend still works if backend returns an empty list.
- Unit tests cover service filtering and detail not found.

## Enquiries and Customer Enquiry Tracking

### Goal

Wire the enquiry form and customer enquiry tab to backend APIs.

### Backend APIs

Implement:

- `POST /api/v1/public/enquiries`
- `GET /api/v1/customer/enquiries`
- `GET /api/v1/customer/enquiries/{enquiryId}`

Optional later:

- `PATCH /api/v1/customer/enquiries/{enquiryId}/cancel`

### Frontend Areas

- `apps/frontend/components/enquiries/EnquiryPanel.tsx`
- `apps/frontend/features/enquiries/enquiry-client.ts`
- `apps/frontend/components/customer/CustomerDashboard.tsx`

### Acceptance Criteria

- Logged-in customer can submit an enquiry.
- Guest user is redirected to login before enquiry submission.
- Created enquiry appears in customer dashboard.
- Customer can only see their own enquiries.
- Validation handles missing phone, event date, and message.
- Unit tests cover create enquiry and customer-only access.

## Suggested Work Order

1. Read `docs/runbooks/auth-integration-pattern.md`.
2. Confirm API request/response in `docs/api/frontend-backend-contract-v1.md`.
3. Add or update backend DTOs.
4. Add controller route.
5. Add service method.
6. Add repository query.
7. Add unit tests.
8. Wire frontend API client.
9. Run:

```bash
cd apps/backend && mvn test
cd apps/frontend && npm run typecheck
```

## Review Checklist

- No entity returned directly from controller.
- No hardcoded customer ID in backend.
- No frontend mock data used when API mode is enabled.
- Handles empty states.
- Handles validation errors.
- Tests pass locally.
