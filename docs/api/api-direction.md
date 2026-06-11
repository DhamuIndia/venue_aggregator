# API Direction

All business logic should pass through the Spring Boot API. The frontend should never connect directly to PostgreSQL.

## Public APIs

- `GET /api/public/halls`
- `GET /api/public/halls/{id}`
- `GET /api/public/halls/{id}/availability`
- `POST /api/public/enquiries`

## Auth APIs

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/forgot-password`

## Hall Owner APIs

- `POST /api/owner/halls`
- `PUT /api/owner/halls/{id}`
- `POST /api/owner/halls/{id}/media`
- `POST /api/owner/halls/{id}/blocked-dates`

## Vendor APIs

- `POST /api/vendors`
- `PUT /api/vendors/me`
- `POST /api/vendors/me/media`
- `GET /api/vendors/me/subscription`

## Subscription APIs

- `GET /api/subscription-plans`
- `POST /api/vendors/me/subscription`
- `POST /api/payments/razorpay/webhook`

## Admin APIs

- `GET /api/admin/halls/pending`
- `PUT /api/admin/halls/{id}/approve`
- `GET /api/admin/vendors/pending`
- `PUT /api/admin/vendors/{id}/approve`
