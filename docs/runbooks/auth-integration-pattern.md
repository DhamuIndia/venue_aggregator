# Auth Integration Pattern

Use the authentication slice as the reference pattern for the next backend modules.

## Completed Integration

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- User registration for `CUSTOMER`, `HALL_OWNER`, and `VENDOR`.
- BCrypt password hashing.
- JWT access tokens and refresh tokens.
- Role-based security for protected routes.
- CORS for local frontend URLs.
- Global `application/problem+json` error responses.
- Frontend API auth client and session storage.
- Frontend session hydration through `/auth/me`.
- Frontend refresh-token flow when access token is near expiry.
- Auth service unit tests.

## Backend

- Base route: `/api/v1`.
- Auth endpoints live under `/api/v1/auth`.
- Controllers only map HTTP and validation DTOs.
- Services own business rules, normalization, duplicate checks, password hashing, and token creation.
- Repositories stay behind services.
- Validation and auth errors return `application/problem+json`.
- Protected modules should read the current user from the JWT principal, not from request body fields.

## Frontend

- Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`.
- Set `NEXT_PUBLIC_AUTH_MODE=api` for backend integration.
- Auth responses are stored as `accessToken`, `refreshToken`, `expiresInSeconds`, `expiresAt`, and `user`.
- UI-only work can still use mock auth by setting `NEXT_PUBLIC_AUTH_MODE=mock`.

## Test Login

For API mode, login uses phone number and password. Local backend seed data can create one test user for each role:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/apps/backend
mvn spring-boot:run -Dspring-boot.run.arguments=--app.seed.dev-data=true
```

Seeded local test accounts:

```text
CUSTOMER: 9876543210 / Password123
HALL_OWNER: 9876501234 / Password123
VENDOR: 9884012345 / Password123
ADMIN: 9000000001 / Password123
```

The seed switch is disabled by default. It can also be enabled with `APP_SEED_DEV_DATA=true`.

Mock mode is frontend-only. If `NEXT_PUBLIC_AUTH_MODE=mock`, the demo buttons use these phone numbers:

```text
CUSTOMER: 9876543210
HALL_OWNER: 9876501234
VENDOR: 9884012345
ADMIN: 9000000001
SUPER_ADMIN: 9000000002
```

The mock accounts are not stored in PostgreSQL and should not be used for backend testing.

## Current Limit

Logout is stateless for now. Add refresh-token persistence/revocation before production launch.
