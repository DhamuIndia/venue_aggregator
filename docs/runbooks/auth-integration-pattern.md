# Auth Integration Pattern

Use the authentication slice as the reference pattern for the next backend modules.

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

## Current Limit

Logout is stateless for now. Add refresh-token persistence/revocation before production launch.
