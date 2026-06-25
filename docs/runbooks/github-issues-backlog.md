# GitHub Issues Backlog

Create these GitHub issues from top to bottom. Use the `Backend integration task` issue template.

## Stream 1: Auth, Public Discovery, Customer

### 1. [P0] Verify auth integration and seed role users

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P0`

Endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Frontend screen: `/auth/login`, `/auth/register`

Acceptance:

- Auth endpoints return the contract shape.
- Seed customer, owner, vendor, and admin users.
- Login works for each role.
- Role routing works in the frontend.

### 2. [P0] Implement public halls search and detail APIs

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P0`

Endpoints:

- `GET /api/v1/public/halls`
- `GET /api/v1/public/halls/{hallId}`

Frontend screen: `/`, `/halls/{id}`

Acceptance:

- Only approved halls are public.
- Search/filter params are accepted.
- Detail returns description, pricing, gallery, reviews, and availability summary fields.
- Empty result returns a valid list shape.

### 3. [P0] Implement public vendor search and detail APIs

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P0`

Endpoints:

- `GET /api/v1/public/vendors`
- `GET /api/v1/public/vendors/{vendorId}`

Frontend screen: `/vendors`, `/vendors/{id}`

Acceptance:

- Only approved vendors are public.
- Category, city, area, price, and verified filters work.
- Detail returns services, packages, gallery, and reviews.

### 4. [P1] Implement customer enquiry creation

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P1`

Endpoint:

- `POST /api/v1/public/enquiries`

Frontend screen: hall detail enquiry form

Acceptance:

- Logged-in customer can create an enquiry.
- Backend derives customer identity from JWT.
- Guest or non-customer users get correct auth errors.
- Validation errors return problem details.

### 5. [P1] Implement customer enquiry history

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P1`

Endpoints:

- `GET /api/v1/customer/enquiries`
- `GET /api/v1/customer/enquiries/{enquiryId}`

Frontend screen: `/customer?tab=enquiries`

Acceptance:

- Customer sees only own enquiries.
- Timeline/status fields match the contract.
- Empty state returns `{ "items": [], "total": 0 }` or contracted equivalent.

### 6. [P1] Implement customer booking lifecycle APIs

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P1`

Endpoints:

- `GET /api/v1/customer/bookings`
- `GET /api/v1/customer/bookings/{bookingId}`

Frontend screen: `/customer?tab=bookings`

Acceptance:

- Customer sees only own bookings.
- Booking status and payment status match contract values.
- Confirmed/completed/cancelled states render correctly.

### 7. [P2] Implement saved halls APIs

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P2`

Endpoints:

- `GET /api/v1/customer/saved-halls`
- `PUT /api/v1/customer/saved-halls/{hallId}`
- `DELETE /api/v1/customer/saved-halls/{hallId}`

Frontend screen: `/customer?tab=saved`

Acceptance:

- Save/remove is idempotent.
- Customer sees only own saved halls.
- Deleted or unavailable halls are handled cleanly.

### 8. [P2] Implement customer verified reviews

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P2`

Endpoints:

- `GET /api/v1/customer/review-eligibility?enquiryId={id}`
- `POST /api/v1/customer/reviews`
- `PUT /api/v1/customer/reviews/{reviewId}`

Frontend screen: `/customer?tab=reviews`

Acceptance:

- Only completed bookings are eligible.
- One active review per enquiry.
- Reviews are created with `verifiedService=true`.

### 9. [P3] Implement customer booking payment APIs

Labels: `backend`, `integration`, `stream:auth-customer`, `priority:P3`

Endpoints:

- `POST /api/v1/customer/bookings/{bookingId}/payments/advance-order`
- `POST /api/v1/customer/bookings/{bookingId}/payments/verify`

Frontend screen: `/customer?tab=bookings`

Acceptance:

- Backend owns advance amount calculation.
- Duplicate payment attempts return conflict.
- Razorpay can be stubbed first, then replaced with real verification.

## Stream 2: Owner Workspace

### 10. [P0] Implement owner hall listing CRUD

Labels: `backend`, `integration`, `stream:owner`, `priority:P0`

Endpoints:

- `GET /api/v1/owner/halls`
- `POST /api/v1/owner/halls`
- `GET /api/v1/owner/halls/{hallId}`
- `PUT /api/v1/owner/halls/{hallId}`

Frontend screen: `/owner`, `/owner?tab=listing`, `/owner/onboarding`

Acceptance:

- Owner can only access own halls.
- Listing create/update returns editable hall shape.
- Draft and approved status values are preserved.

### 11. [P0] Implement owner hall submit for approval

Labels: `backend`, `integration`, `stream:owner`, `priority:P0`

Endpoint:

- `POST /api/v1/owner/halls/{hallId}/submit`

Frontend screen: `/owner?tab=listing`

Acceptance:

- Submit validates required listing fields.
- Response returns `PENDING_APPROVAL`.
- Owner cannot submit another owner's hall.

### 12. [P1] Implement owner enquiry inbox and response

Labels: `backend`, `integration`, `stream:owner`, `priority:P1`

Endpoints:

- `GET /api/v1/owner/halls/{hallId}/enquiries`
- `PATCH /api/v1/owner/enquiries/{enquiryId}/status`

Frontend screen: `/owner?tab=enquiries`

Acceptance:

- Owner sees only enquiries for own halls.
- Confirm creates or activates a booking.
- Decline does not create a booking.
- Invalid transitions return conflict.

### 13. [P1] Implement owner booking lifecycle

Labels: `backend`, `integration`, `stream:owner`, `priority:P1`

Endpoints:

- `GET /api/v1/owner/halls/{hallId}/bookings`
- `PATCH /api/v1/owner/bookings/{bookingId}/status`

Frontend screen: `/owner?tab=bookings`

Acceptance:

- Owner sees only own hall bookings.
- Complete booking unlocks customer review eligibility.
- Cancel/complete transitions follow the contract.

### 14. [P1] Implement owner availability and blocked dates

Labels: `backend`, `integration`, `stream:owner`, `priority:P1`

Endpoints:

- `GET /api/v1/owner/halls/{hallId}/availability`
- `POST /api/v1/owner/halls/{hallId}/blocked-dates`
- `DELETE /api/v1/owner/halls/{hallId}/blocked-dates/{blockId}`

Frontend screen: `/owner?tab=availability`

Acceptance:

- Confirmed bookings and blocked dates are returned together.
- Date/slot conflicts are prevented.
- Delete is scoped to owner hall.

### 15. [P2] Implement owner media upload metadata APIs

Labels: `backend`, `integration`, `stream:owner`, `priority:P2`

Endpoints:

- `POST /api/v1/uploads/presign`
- `POST /api/v1/owner/halls/{hallId}/media`
- `PATCH /api/v1/owner/halls/{hallId}/media/{mediaId}`
- `DELETE /api/v1/owner/halls/{hallId}/media/{mediaId}`

Frontend screen: `/owner?tab=media`

Acceptance:

- Presign supports `OWNER_HALL_MEDIA`.
- Metadata saves `storageKey`, URL, caption, cover, sort order.
- Cover update normalizes previous cover.

### 16. [P2] Implement owner reviews API

Labels: `backend`, `integration`, `stream:owner`, `priority:P2`

Endpoint:

- `GET /api/v1/owner/halls/{hallId}/reviews`

Frontend screen: `/owner?tab=reviews`

Acceptance:

- Owner sees reviews for own hall only.
- Average rating and total reviews are returned.

### 17. [P3] Implement owner reports API

Labels: `backend`, `integration`, `stream:owner`, `priority:P3`

Endpoint:

- `GET /api/v1/owner/halls/{hallId}/reports/summary`

Frontend screen: `/owner?tab=reports`

Acceptance:

- Response returns aggregated counts and trends.
- No raw unrelated customer data is exposed.

## Stream 3: Vendor, Admin, Cross-Cutting

### 18. [P0] Implement vendor profile APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P0`

Endpoints:

- `GET /api/v1/vendor/profile`
- `PUT /api/v1/vendor/profile`
- `POST /api/v1/vendor/profile/submit`

Frontend screen: `/vendor`, `/vendor/onboarding`

Acceptance:

- Vendor can only edit own profile.
- Submit returns `PENDING_APPROVAL`.
- Public profile updates only after approval.

### 19. [P1] Implement vendor leads APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P1`

Endpoints:

- `GET /api/v1/vendor/leads`
- `GET /api/v1/vendor/leads/{leadId}`
- `PATCH /api/v1/vendor/leads/{leadId}/status`

Frontend screen: `/vendor?tab=leads`

Acceptance:

- Vendor sees only own leads.
- Lead transitions follow the contract.
- Terminal statuses cannot be changed.

### 20. [P1] Implement vendor packages APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P1`

Endpoints:

- `GET /api/v1/vendor/packages`
- `POST /api/v1/vendor/packages`
- `PUT /api/v1/vendor/packages/{packageId}`
- `DELETE /api/v1/vendor/packages/{packageId}`

Frontend screen: `/vendor?tab=services`

Acceptance:

- Vendor package ownership is enforced.
- Price and inclusions validation is applied.
- Deleted packages disappear from vendor workspace.

### 21. [P2] Implement vendor media upload metadata APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P2`

Endpoints:

- `POST /api/v1/uploads/presign`
- `GET /api/v1/vendor/media`
- `POST /api/v1/vendor/media`
- `PATCH /api/v1/vendor/media/{mediaId}`
- `DELETE /api/v1/vendor/media/{mediaId}`

Frontend screen: `/vendor?tab=portfolio`

Acceptance:

- Presign supports `VENDOR_PORTFOLIO`.
- Media ownership is enforced.
- Cover update normalizes previous cover.

### 22. [P2] Implement vendor subscription APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P2`

Endpoints:

- `GET /api/v1/public/subscription-plans`
- `GET /api/v1/vendor/subscription`
- `POST /api/v1/vendor/subscription/orders`
- `POST /api/v1/vendor/subscription/verify`

Frontend screen: `/vendor?tab=subscription`

Acceptance:

- Plans come from backend.
- Order creation uses backend-owned pricing.
- Razorpay can be stubbed first.

### 23. [P2] Implement admin approvals

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P2`

Endpoints:

- `GET /api/v1/admin/halls`
- `PATCH /api/v1/admin/halls/{hallId}/review`
- `GET /api/v1/admin/vendors`
- `PATCH /api/v1/admin/vendors/{vendorId}/review`

Frontend screen: `/admin?tab=venues`, `/admin?tab=vendors`

Acceptance:

- Admin can approve/reject halls and vendors.
- Rejection reason is required.
- Every mutation writes an audit event.

### 24. [P2] Implement admin operations APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P2`

Endpoints:

- `GET /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{userId}/status`
- `GET /api/v1/admin/reviews`
- `PATCH /api/v1/admin/reviews/{reviewId}/moderation`
- `GET /api/v1/admin/enquiries`
- `GET /api/v1/admin/audit-events`

Frontend screen: `/admin?tab=users`, `/admin?tab=reviews`, `/admin?tab=enquiries`

Acceptance:

- Admin user status rules are enforced.
- Review moderation keeps audit history.
- Enquiry and audit lists support admin visibility.

### 25. [P3] Implement reports, notifications, and webhook APIs

Labels: `backend`, `integration`, `stream:vendor-admin`, `priority:P3`

Endpoints:

- `GET /api/v1/vendor/reports/summary`
- `GET /api/v1/admin/reports/summary`
- `GET /api/v1/notifications`
- `PATCH /api/v1/notifications/{notificationId}/read`
- `PATCH /api/v1/notifications/read-all`
- `POST /api/v1/payments/razorpay/webhook`

Frontend screen: `/vendor?tab=reports`, `/admin?tab=reports`, notification bell

Acceptance:

- Reports return aggregated numbers.
- Notifications are scoped to logged-in user.
- Webhook processing is idempotent.
