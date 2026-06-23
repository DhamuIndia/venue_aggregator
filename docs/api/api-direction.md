# API Direction

All business logic should pass through the Spring Boot API. The frontend should never connect directly to PostgreSQL.

## Public APIs

- `GET /api/v1/public/halls`
- `GET /api/v1/public/halls/{id}`
- `GET /api/v1/public/halls/{id}/availability`
- `POST /api/v1/public/enquiries`

## Auth APIs

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/forgot-password`

## Hall Owner APIs

- `POST /api/v1/owner/halls`
- `PUT /api/v1/owner/halls/{id}`
- `POST /api/v1/owner/halls/{id}/media`
- `POST /api/v1/owner/halls/{id}/blocked-dates`

## Vendor APIs

- `POST /api/v1/vendors`
- `PUT /api/v1/vendors/me`
- `POST /api/v1/vendors/me/media`
- `GET /api/v1/vendors/me/subscription`

## Subscription APIs

- `GET /api/v1/subscription-plans`
- `POST /api/v1/vendors/me/subscription`
- `POST /api/v1/payments/razorpay/webhook`

## Admin APIs

- `GET /api/v1/admin/halls/pending`
- `PUT /api/v1/admin/halls/{id}/approve`
- `GET /api/v1/admin/vendors/pending`
- `PUT /api/v1/admin/vendors/{id}/approve`
