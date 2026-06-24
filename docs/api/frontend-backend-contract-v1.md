# Venue Aggregator API Contract v1

This contract covers the current customer, hall owner, and admin UI. Spring Boot is the system of record; frontend mock data must be replaced without changing these response shapes.

## Conventions

- Base path: `/api/v1`
- Content type: `application/json`
- Authentication: `Authorization: Bearer <accessToken>`
- Dates: `YYYY-MM-DD`; timestamps: ISO 8601 UTC
- Currency: whole INR rupees as integers, for example `120000`
- IDs: opaque strings; clients must not derive meaning from them
- List responses use zero-based `page` and default `size=20`
- Mutating requests accept `Idempotency-Key` when a retry could create duplicates

### Roles

`CUSTOMER`, `HALL_OWNER`, `VENDOR`, `ADMIN`, `SUPER_ADMIN`

### Pagination

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Error response

Use RFC 9457 problem details for every non-2xx response.

```json
{
  "type": "https://venue.example/problems/validation-error",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/public/enquiries",
  "traceId": "01J...",
  "fieldErrors": [{ "field": "eventDate", "message": "Date must be in the future" }]
}
```

Expected statuses: `400`, `401`, `403`, `404`, `409`, `422`, `429`, and `500`.

## Core Resources

### User

```json
{
  "id": "customer-101",
  "fullName": "Priya Raman",
  "phone": "9876543210",
  "email": "priya@example.com",
  "role": "CUSTOMER",
  "status": "ACTIVE"
}
```

User status: `ACTIVE`, `SUSPENDED`, `DEACTIVATED`.

### Hall summary

```json
{
  "id": "emerald-convention-centre",
  "name": "Emerald Convention Centre",
  "city": "Chennai",
  "area": "ECR",
  "capacity": 700,
  "startingPrice": 120000,
  "rating": 4.8,
  "reviewCount": 126,
  "coverImageUrl": "https://cdn.example.com/halls/emerald/cover.webp",
  "venueType": "MARRIAGE_HALL",
  "amenities": ["AIR_CONDITIONED", "PARKING", "DINING_HALL"],
  "verified": true,
  "availableThisMonth": true
}
```

Venue type: `MARRIAGE_HALL`, `BANQUET_HALL`, `MINI_HALL`, `CONVENTION_CENTRE`.

Hall listing status: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`.

### Enquiry

```json
{
  "id": "ENQ-022690",
  "hallId": "emerald-convention-centre",
  "hallName": "Emerald Convention Centre",
  "customerId": "customer-101",
  "eventDate": "2026-07-18",
  "eventType": "Wedding",
  "guestCount": 450,
  "slot": "EVENING",
  "notes": "Please share catering options.",
  "status": "PENDING_OWNER_RESPONSE",
  "submittedAt": "2026-06-22T10:30:00Z",
  "updatedAt": "2026-06-22T10:30:00Z"
}
```

Slots: `MORNING`, `EVENING`, `FULL_DAY`.

Enquiry transitions:

- `PENDING_OWNER_RESPONSE -> CONFIRMED | DECLINED`
- `CONFIRMED -> COMPLETED` after the event or admin reconciliation
- Terminal statuses are not changed by customers

## Authentication

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | Public | Register customer or hall owner |
| `POST` | `/auth/login` | Public | Create session |
| `POST` | `/auth/refresh` | Refresh token | Rotate tokens |
| `POST` | `/auth/logout` | User | Client logout for now; backend can revoke refresh token after token storage is added |
| `POST` | `/auth/forgot-password` | Public | Start reset flow |
| `GET` | `/auth/me` | User | Return current user |

Register request:

```json
{
  "fullName": "Arun Kumar",
  "phone": "9876501234",
  "email": "arun@example.com",
  "password": "secret-value",
  "role": "HALL_OWNER"
}
```

Login response:

```json
{
  "accessToken": "jwt",
  "refreshToken": "jwt-refresh-token",
  "expiresInSeconds": 900,
  "user": {
    "id": "owner-201",
    "fullName": "Arun Kumar",
    "phone": "9876501234",
    "role": "HALL_OWNER",
    "status": "ACTIVE"
  }
}
```

## Public Hall Discovery

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/public/halls` | Search approved halls |
| `GET` | `/public/halls/{hallId}` | Hall details, media, pricing, reviews |
| `GET` | `/public/halls/{hallId}/availability?from=YYYY-MM-DD&to=YYYY-MM-DD` | Available, blocked, and booked slots |

Search query parameters: `q`, `city`, `area`, `venueType`, `minCapacity`, `maxPrice`, `eventDate`, `slot`, `amenity`, `sort`, `page`, `size`.

`sort`: `RELEVANCE`, `RATING_DESC`, `PRICE_ASC`, `CAPACITY_DESC`.

Hall detail extends the summary with `description`, `address`, `pincode`, `gallery`, `pricing`, `ownerResponseRate`, and `listingStatus`.

## Public Vendor Discovery

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/public/vendors` | Search approved vendor profiles |
| `GET` | `/public/vendors/{vendorId}` | Vendor profile, services, packages, media, and reviews |
| `POST` | `/public/vendor-leads` | Customer requests a quote |

Vendor search query parameters: `q`, `city`, `area`, `category`, `maxPrice`, `verified`, `sort`, `page`, `size`.

Vendor category: `CATERING`, `DECORATION`, `PHOTOGRAPHY`, `BRIDAL_MAKEUP`, `MUSIC_AND_DJ`, `EVENT_PLANNING`.

Vendor listing status: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`.

Vendor search/detail response items should include:

```json
{
  "id": "saffron-leaf-catering",
  "businessName": "Saffron Leaf Catering",
  "ownerName": "Manoj Krishnan",
  "category": "CATERING",
  "city": "Chennai",
  "area": "Adyar",
  "rating": 4.9,
  "reviewCount": 84,
  "startingPrice": 650,
  "imageUrl": "https://example.com/vendor-cover.jpg",
  "galleryUrls": ["https://example.com/vendor-1.jpg"],
  "verified": true,
  "responseTime": "Within 2 hours",
  "completedEvents": 212,
  "services": ["Wedding catering", "Live counters"],
  "description": "South Indian celebration menus with transparent per-plate pricing.",
  "packages": [
    {
      "id": "PKG-C1",
      "name": "Classic celebration",
      "description": "A balanced vegetarian menu for intimate events.",
      "price": 650,
      "includes": ["Welcome drink", "18-item meal"]
    }
  ],
  "reviews": [
    {
      "id": "VREV-81",
      "customerName": "Harini S.",
      "rating": 5,
      "eventType": "Wedding",
      "comment": "The service team was smooth for all guests.",
      "eventDate": "2026-05-12",
      "verifiedService": true
    }
  ]
}
```

Create vendor lead request:

```json
{
  "vendorId": "saffron-leaf-catering",
  "eventDate": "2026-08-02",
  "eventType": "Reception",
  "location": "Velachery, Chennai",
  "service": "Wedding catering",
  "budget": 420000,
  "notes": "Dinner for around 550 guests."
}
```

The server derives customer and vendor identity. Return `201` with status `NEW`.

Create vendor lead response:

```json
{
  "id": "LEAD-804231",
  "vendorId": "saffron-leaf-catering",
  "vendorName": "Saffron Leaf Catering",
  "customerId": "customer-411",
  "customerName": "Deepa Raj",
  "eventDate": "2026-08-02",
  "eventType": "Reception",
  "location": "Velachery, Chennai",
  "service": "Wedding catering",
  "budget": 420000,
  "notes": "Dinner for around 550 guests.",
  "status": "NEW",
  "submittedAt": "2026-06-23T10:30:00Z"
}
```

Return `401` when the user is not logged in, `403` when the logged-in user is not a customer, and `400` for invalid event date, service, location, or budget.

## Customer APIs

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/public/enquiries` | Submit an authenticated enquiry |
| `GET` | `/customer/enquiries` | Customer enquiry history |
| `GET` | `/customer/enquiries/{enquiryId}` | Enquiry details and timeline |
| `GET` | `/customer/bookings` | Customer booking lifecycle list |
| `GET` | `/customer/bookings/{bookingId}` | Customer booking detail |
| `POST` | `/customer/bookings/{bookingId}/payments/advance-order` | Create Razorpay advance payment order |
| `POST` | `/customer/bookings/{bookingId}/payments/verify` | Verify Razorpay advance payment |
| `GET` | `/customer/saved-halls` | Saved halls |
| `PUT` | `/customer/saved-halls/{hallId}` | Save a hall; idempotent |
| `DELETE` | `/customer/saved-halls/{hallId}` | Remove saved hall |
| `GET` | `/customer/review-eligibility?enquiryId={id}` | Verify completed service |
| `POST` | `/customer/reviews` | Create a verified review |
| `PUT` | `/customer/reviews/{reviewId}` | Edit own review |

Create enquiry request:

```json
{
  "hallId": "emerald-convention-centre",
  "eventDate": "2026-07-18",
  "eventType": "Wedding",
  "guestCount": 450,
  "slot": "EVENING",
  "notes": "Please share catering options."
}
```

The server derives `customerId`, `hallName`, status, and timestamps. Return `201` with the enquiry resource.

Saved halls response:

```json
{
  "items": [
    {
      "id": "emerald-convention-centre",
      "hallId": "emerald-convention-centre",
      "name": "Emerald Convention Centre",
      "city": "Chennai",
      "area": "ECR",
      "capacity": 900,
      "startingPrice": 125000,
      "rating": 4.8,
      "reviewCount": 86,
      "imageUrl": "https://cdn.example.com/halls/emerald/cover.jpg",
      "venueType": "MARRIAGE_HALL",
      "isVerified": true,
      "availableThisMonth": true
    }
  ],
  "total": 1
}
```

`PUT /customer/saved-halls/{hallId}` is idempotent and may return either the saved hall item or `204`. `DELETE /customer/saved-halls/{hallId}` should return `204` even when the hall was not already saved. Return `401` when the user is not logged in, `403` when the logged-in user is not a customer, and `404` when the hall does not exist.

Customer bookings response:

```json
{
  "items": [
    {
      "id": "BOOK-2048",
      "bookingId": "BOOK-2048",
      "enquiryId": "ENQ-2048",
      "hallId": "emerald-convention-centre",
      "hallName": "Emerald Convention Centre",
      "customerId": "customer-101",
      "customerName": "Priya Raman",
      "eventDate": "2026-07-18",
      "eventType": "Wedding",
      "guestCount": 450,
      "slot": "FULL_DAY",
      "status": "CONFIRMED",
      "amount": 125000,
      "paymentStatus": "ADVANCE_PENDING",
      "confirmedAt": "2026-06-23T10:30:00Z",
      "updatedAt": "2026-06-23T10:30:00Z"
    }
  ],
  "total": 1
}
```

Booking status values: `REQUESTED`, `CONFIRMED`, `CANCELLED`, `COMPLETED`. Payment status values for now: `NOT_STARTED`, `ADVANCE_PENDING`, `ADVANCE_PAID`, `REFUNDED`. Customer booking routes must only return bookings belonging to the logged-in customer.

Create booking advance order request:

```json
{
  "bookingId": "BOOK-2048"
}
```

Create booking advance order response:

```json
{
  "orderId": "order_Rzp_123",
  "razorpayOrderId": "order_Rzp_123",
  "bookingId": "BOOK-2048",
  "amount": 25000,
  "currency": "INR",
  "status": "CREATED",
  "keyId": "rzp_test_xxxxx",
  "checkoutUrl": "https://checkout.razorpay.com/v1/checkout/embedded/order_Rzp_123"
}
```

The frontend sends only `bookingId`. The backend owns the advance amount calculation, creates the Razorpay order, and must reject payment attempts for bookings that are not `CONFIRMED`, already paid, cancelled, completed, or not owned by the logged-in customer.

Verify booking advance payment request:

```json
{
  "bookingId": "BOOK-2048",
  "orderId": "order_Rzp_123",
  "razorpayPaymentId": "pay_Rzp_456",
  "razorpaySignature": "signed_payload"
}
```

Verify booking advance payment response should return the updated booking with `paymentStatus=ADVANCE_PAID`. Return `400` for invalid signatures, `403` for bookings outside the logged-in customer, `404` for missing bookings/orders, and `409` for duplicate or stale payment attempts. Razorpay webhooks should be idempotent and update the same payment state.

Review eligibility response:

```json
{
  "eligible": true,
  "enquiryId": "ENQ-1884",
  "hallId": "marigold-mini-hall",
  "hallName": "Marigold Mini Hall",
  "eventDate": "2026-05-10",
  "eventType": "Birthday celebration",
  "reason": null
}
```

Only the customer attached to a `COMPLETED` enquiry may review that hall. Enforce one active review per enquiry. Reviews created through this route have `verifiedService=true`.

Create verified review request:

```json
{
  "enquiryId": "ENQ-1884",
  "hallId": "marigold-mini-hall",
  "rating": 5,
  "comment": "The hall was clean, well managed, and the team coordinated everything smoothly."
}
```

Create verified review response:

```json
{
  "id": "REV-1884",
  "enquiryId": "ENQ-1884",
  "hallId": "marigold-mini-hall",
  "rating": 5,
  "comment": "The hall was clean, well managed, and the team coordinated everything smoothly.",
  "submittedAt": "2026-06-23T10:30:00Z",
  "verifiedService": true,
  "status": "PENDING_MODERATION"
}
```

Return `403` when the enquiry does not belong to the logged-in customer, `409` when a review already exists for the enquiry, and `400` for invalid rating or comment length.

## Hall Owner APIs

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/owner/dashboard` | Summary counts and listing health |
| `GET` | `/owner/halls` | Owner halls |
| `POST` | `/owner/halls` | Create draft hall |
| `GET` | `/owner/halls/{hallId}` | Owner-editable hall detail |
| `PUT` | `/owner/halls/{hallId}` | Replace editable listing fields |
| `POST` | `/owner/halls/{hallId}/submit` | Submit for admin approval |
| `GET` | `/owner/halls/{hallId}/enquiries` | Hall enquiry inbox |
| `PATCH` | `/owner/enquiries/{enquiryId}/status` | Confirm or decline enquiry |
| `GET` | `/owner/halls/{hallId}/bookings` | Hall booking lifecycle list |
| `PATCH` | `/owner/bookings/{bookingId}/status` | Complete or cancel booking |
| `GET` | `/owner/halls/{hallId}/reports/summary` | Hall performance reports |
| `GET` | `/owner/halls/{hallId}/availability` | Calendar data |
| `POST` | `/owner/halls/{hallId}/blocked-dates` | Block a slot |
| `DELETE` | `/owner/halls/{hallId}/blocked-dates/{blockId}` | Remove owner block |
| `POST` | `/owner/halls/{hallId}/media` | Save uploaded media metadata |
| `PATCH` | `/owner/halls/{hallId}/media/{mediaId}` | Change caption, order, or cover |
| `DELETE` | `/owner/halls/{hallId}/media/{mediaId}` | Remove media |
| `GET` | `/owner/halls/{hallId}/reviews` | Hall reviews |

Create/update owner hall request:

```json
{
  "name": "Pearl Grand Hall",
  "hallName": "Pearl Grand Hall",
  "venueType": "MARRIAGE_HALL",
  "description": "Spacious wedding venue with dining and parking.",
  "city": "Chennai",
  "area": "Velachery",
  "pincode": "600042",
  "capacity": 850,
  "capacityMax": 850,
  "startingPrice": 145000,
  "pricing": {
    "morningPrice": 75000,
    "eveningPrice": 90000,
    "fullDayPrice": 145000
  },
  "amenities": ["Air conditioned", "Parking", "Dining hall"],
  "acAvailable": true,
  "carParking": true,
  "diningAvailable": true,
  "generatorAvailable": true,
  "liftAvailable": false
}
```

`POST /owner/halls` creates a draft and returns the saved listing with `id`, `status=DRAFT`, and `updatedAt`. `PUT /owner/halls/{hallId}` updates the draft. `POST /owner/halls/{hallId}/submit` validates the latest draft and returns `status=PENDING_APPROVAL`.

Enquiry status request:

```json
{ "status": "CONFIRMED" }
```

When an owner confirms an enquiry, the backend should create or activate a booking for that enquiry and block the selected hall/date/slot from double-booking. Declining an enquiry does not create a booking.

Owner booking status request:

```json
{ "status": "COMPLETED" }
```

Allowed owner booking status transitions:

| From | To | Meaning |
| --- | --- | --- |
| `REQUESTED` | `CONFIRMED` | Owner confirms booking request |
| `REQUESTED` | `CANCELLED` | Owner cancels before confirmation |
| `CONFIRMED` | `CANCELLED` | Owner cancels confirmed event |
| `CONFIRMED` | `COMPLETED` | Event finished; customer becomes review-eligible |

Return the updated booking resource. Return `403` when the booking does not belong to the owner hall, `404` when it does not exist, and `409` when the booking is stale, cancelled, completed, or the slot conflicts with another confirmed booking.

Use optimistic locking (`version` field or `If-Match`) for enquiry status, booking status, and listing edits. Return `409` when another actor already changed the resource.

Blocked date request:

```json
{
  "date": "2026-07-15",
  "slot": "FULL_DAY",
  "reason": "Maintenance"
}
```

## Vendor APIs

All routes require `VENDOR` and ownership of the vendor profile.

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/vendor/dashboard` | Lead, booking, profile, and plan summary |
| `GET` | `/vendor/profile` | Vendor-editable profile |
| `PUT` | `/vendor/profile` | Replace business and service-area fields |
| `POST` | `/vendor/profile/submit` | Submit profile for admin approval |
| `GET` | `/vendor/packages` | Service packages |
| `POST` | `/vendor/packages` | Create package |
| `PUT` | `/vendor/packages/{packageId}` | Update package |
| `DELETE` | `/vendor/packages/{packageId}` | Remove package |
| `GET` | `/vendor/leads` | Vendor lead inbox |
| `GET` | `/vendor/leads/{leadId}` | Lead detail |
| `PATCH` | `/vendor/leads/{leadId}/status` | Update lead workflow |
| `GET` | `/vendor/reports/summary?vendorId={vendorId}` | Vendor lead and booking reports |
| `GET` | `/vendor/media` | Vendor portfolio media |
| `POST` | `/vendor/media` | Save uploaded media metadata |
| `PATCH` | `/vendor/media/{mediaId}` | Change caption, order, or cover |
| `DELETE` | `/vendor/media/{mediaId}` | Remove media |
| `GET` | `/vendor/reviews` | Verified customer reviews |

Vendor profile request:

```json
{
  "businessName": "Saffron Leaf Catering",
  "category": "CATERING",
  "city": "Chennai",
  "area": "Adyar",
  "serviceRadius": 25,
  "yearsInBusiness": 5,
  "description": "South Indian celebration menus with trained event service staff.",
  "services": ["Wedding catering", "Live counters", "Traditional meals"],
  "packageName": "Classic celebration",
  "startingPrice": 650,
  "packageDescription": "A balanced vegetarian menu for intimate events."
}
```

Vendor profile response should return the same editable fields plus `status` (`DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`) and `updatedAt`. `POST /vendor/profile/submit` does not require a body; it validates the latest saved draft and returns the profile with `status=PENDING_APPROVAL`.

Vendor package request:

```json
{
  "name": "Grand wedding feast",
  "description": "Expanded wedding menu with live counters.",
  "price": 1050,
  "includes": ["Two welcome drinks", "28-item meal", "Two live counters"]
}
```

Vendor package response:

```json
{
  "id": "PKG-C2",
  "name": "Grand wedding feast",
  "description": "Expanded wedding menu with live counters.",
  "price": 1050,
  "includes": ["Two welcome drinks", "28-item meal", "Two live counters"]
}
```

Return `400` for invalid package name, price, or inclusions. Return `403` when the package does not belong to the logged-in vendor and `409` when a stale update conflicts.

Vendor media request:

```json
{
  "url": "https://cdn.example.com/vendors/saffron/portfolio-1.jpg",
  "mediaUrl": "https://cdn.example.com/vendors/saffron/portfolio-1.jpg",
  "storageKey": "vendors/saffron/portfolio-1.jpg",
  "fileName": "portfolio-1.jpg",
  "caption": "Reception buffet setup",
  "isCover": false,
  "primary": false,
  "sortOrder": 2,
  "mediaType": "IMAGE",
  "type": "IMAGE"
}
```

Vendor media response should include `id`, `url`, `caption`, `isCover`, `sortOrder`, and optional `storageKey`. Cover updates should normalize the previous cover to `isCover=false`.

Vendor lead status: `NEW`, `CONTACTED`, `QUOTE_SENT`, `BOOKED`, `DECLINED`.

Lead status request:

```json
{ "status": "QUOTE_SENT" }
```

Return the updated lead resource. Return `403` when the lead does not belong to the logged-in vendor and `409` when a stale update conflicts.

Allowed transitions:

- `NEW -> CONTACTED | QUOTE_SENT | DECLINED`
- `CONTACTED -> QUOTE_SENT | BOOKED | DECLINED`
- `QUOTE_SENT -> BOOKED | DECLINED`
- `BOOKED` and `DECLINED` are terminal for the vendor

### Vendor subscriptions

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/public/subscription-plans` | Active vendor plans |
| `GET` | `/vendor/subscription` | Current plan and renewal state |
| `POST` | `/vendor/subscription/orders` | Create a Razorpay order |
| `POST` | `/vendor/subscription/verify` | Verify checkout signature |
| `POST` | `/payments/razorpay/webhook` | Process signed subscription and booking payment events |

The frontend sends only `planId` when creating an order. The backend owns plan pricing, creates the Razorpay order, validates signatures, handles idempotent webhooks, and activates the subscription only after a verified payment event.

Subscription plan response item:

```json
{
  "id": "GROWTH",
  "name": "Growth",
  "price": 2499,
  "currency": "INR",
  "billingCycle": "MONTHLY",
  "description": "For teams ready to expand across the city.",
  "features": ["Unlimited customer leads", "Unlimited packages"],
  "leadLimit": null,
  "isPopular": true
}
```

Vendor subscription response:

```json
{
  "planId": "STARTER",
  "status": "ACTIVE",
  "currentPeriodEnd": "2026-07-23T00:00:00Z",
  "pendingOrderId": null
}
```

Create subscription order request:

```json
{ "planId": "GROWTH" }
```

Create subscription order response:

```json
{
  "orderId": "order_RZP_123",
  "planId": "GROWTH",
  "amount": 2499,
  "currency": "INR",
  "status": "CREATED",
  "keyId": "rzp_test_xxxxx",
  "checkoutUrl": null
}
```

Return `400` for invalid plans, `403` when the vendor profile cannot subscribe, and `409` when an active order already exists for the same plan.

## Notification APIs

All notification routes require authentication. The backend derives the recipient from the logged-in user and must not accept user IDs from the client.

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/notifications` | Logged-in user notification feed |
| `PATCH` | `/notifications/{notificationId}/read` | Mark one notification as read |
| `PATCH` | `/notifications/read-all` | Mark all current user notifications read |

Notification response:

```json
{
  "items": [
    {
      "id": "NOTIF-4102",
      "type": "BOOKING",
      "title": "Booking confirmed",
      "message": "Emerald Convention Centre confirmed your Wedding booking for 18 July 2026.",
      "createdAt": "2026-06-24T09:30:00Z",
      "readAt": null,
      "actionHref": "/customer?tab=bookings"
    }
  ],
  "unreadCount": 1
}
```

Notification type values: `ENQUIRY`, `BOOKING`, `PAYMENT`, `REVIEW`, `SYSTEM`. `actionHref` should be a frontend route only, not an external URL.

Recommended events to emit:

- Customer: enquiry submitted, owner confirmed/declined, booking created, advance payment pending/paid, booking completed, review requested
- Owner: new enquiry, booking created, customer advance paid, booking cancellation/completion, new verified review
- Vendor: new lead, quote status changes, subscription payment result
- Admin: new hall/vendor approval request, reported review

Notification writes should happen after the business action succeeds. Delivery can be asynchronous, but the API mutation should not depend on SMS/email success.

## Admin APIs

All routes require `ADMIN` or `SUPER_ADMIN`. Every mutation writes an immutable audit event containing actor, action, resource, reason, previous state, new state, and timestamp.

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/admin/dashboard` | Approval, report, and enquiry counts |
| `GET` | `/admin/halls?status=PENDING_APPROVAL` | Hall moderation queue |
| `GET` | `/admin/halls/{hallId}` | Submitted listing and documents |
| `PATCH` | `/admin/halls/{hallId}/review` | Approve or reject hall |
| `GET` | `/admin/vendors?status=PENDING_APPROVAL` | Vendor moderation queue |
| `PATCH` | `/admin/vendors/{vendorId}/review` | Approve or reject vendor |
| `GET` | `/admin/reviews?status=REPORTED` | Review moderation queue |
| `PATCH` | `/admin/reviews/{reviewId}/moderation` | Publish, hide, or reject review |
| `GET` | `/admin/enquiries` | Track enquiries across halls |
| `GET` | `/admin/users` | Search and filter platform users |
| `PATCH` | `/admin/users/{userId}/status` | Activate or suspend a user |
| `GET` | `/admin/reports/summary` | Platform analytics summary |
| `GET` | `/admin/audit-events` | Search immutable audit history |

Approval request:

```json
{
  "decision": "APPROVED",
  "reason": "All venue and ownership documents verified."
}
```

Decision: `APPROVED`, `REJECTED`. A reason is required for rejection. Return the updated resource and `reviewedBy`, `reviewedAt`.

Hall moderation list items should include:

```json
{
  "id": "HALL-3201",
  "name": "Pearl Grand Hall",
  "ownerName": "Suresh Babu",
  "ownerPhone": "9840012233",
  "location": "Velachery, Chennai",
  "venueType": "Marriage Hall",
  "capacity": 850,
  "startingPrice": 145000,
  "submittedAt": "2026-06-22T08:45:00Z",
  "imageUrl": "https://example.com/hall.jpg",
  "status": "PENDING_APPROVAL",
  "documents": {
    "ownership": true,
    "identity": true,
    "address": true
  }
}
```

Vendor moderation list items should include `id`, `businessName`, `contactName`, `category`, `city`, `submittedAt`, and `status`.

Review moderation request:

```json
{
  "status": "HIDDEN",
  "reason": "Contains personal contact information."
}
```

Review status: `PENDING`, `PUBLISHED`, `REPORTED`, `HIDDEN`, `REJECTED`.

Reported review list items should include `id`, `hallName`, `customerName`, `rating`, `comment`, `reportReason`, `verifiedService`, and `status`.

Admin user list response:

```json
{
  "items": [
    {
      "id": "customer-101",
      "fullName": "Priya Raman",
      "phone": "9876543210",
      "email": "priya@example.com",
      "role": "CUSTOMER",
      "status": "ACTIVE",
      "joinedAt": "2026-06-12T09:30:00Z",
      "lastActiveAt": "2026-06-24T08:45:00Z",
      "city": "Chennai"
    }
  ],
  "total": 1
}
```

Supported query params: `role`, `status`, `q`, `page`, and `size`. User roles: `CUSTOMER`, `HALL_OWNER`, `VENDOR`, `ADMIN`, `SUPER_ADMIN`. User status values: `ACTIVE`, `SUSPENDED`, `PENDING_VERIFICATION`.

User status request:

```json
{
  "status": "SUSPENDED",
  "reason": "Account suspended by admin for platform review."
}
```

Return the updated user. Return `403` when the admin is not allowed to change that user, `404` when the user does not exist, and `409` when attempting an invalid transition such as self-suspending, suspending the last active super admin, or activating a user whose required verification is incomplete.

## Reports and Analytics

Report endpoints return already-aggregated numbers for the UI. The backend owns all calculations and must derive owner/vendor access from the authenticated user.

Shared trend item:

```json
{
  "label": "Jun",
  "enquiries": 284,
  "bookings": 74,
  "revenue": 8420000
}
```

Admin report response:

```json
{
  "totalUsers": 1248,
  "activeListings": 186,
  "monthlyEnquiries": 284,
  "confirmedBookings": 74,
  "bookingRevenue": 8420000,
  "vendorRevenue": 284000,
  "conversionRate": 26,
  "trends": [{ "label": "Jun", "enquiries": 284, "bookings": 74, "revenue": 8420000 }],
  "topCities": [{ "city": "Chennai", "enquiries": 148, "bookings": 42 }]
}
```

Owner hall report response:

```json
{
  "enquiries": 42,
  "confirmedBookings": 11,
  "completedBookings": 5,
  "estimatedRevenue": 1385000,
  "conversionRate": 26,
  "averageRating": 4.8,
  "occupancyRate": 62,
  "trends": [{ "label": "Jun", "enquiries": 9, "bookings": 2, "revenue": 260000 }],
  "eventMix": [{ "eventType": "Wedding", "count": 6 }]
}
```

Vendor report response:

```json
{
  "leads": 34,
  "contacted": 24,
  "quotesSent": 18,
  "booked": 7,
  "bookedValue": 2140000,
  "conversionRate": 21,
  "averageBudget": 305714,
  "responseRate": 71,
  "trends": [{ "label": "Jun", "enquiries": 9, "bookings": 2, "revenue": 500000 }],
  "serviceMix": [{ "service": "Wedding catering", "count": 13 }]
}
```

Return `403` when the requested owner hall or vendor profile is outside the logged-in user's ownership. Return zeros and empty arrays when there is no activity yet.

## Upload Contract

Do not send large media through the Spring application.

1. `POST /uploads/presign` with `fileName`, `contentType`, `sizeBytes`, and `purpose`.
2. API returns `uploadUrl`, `storageKey`, required headers, and expiry.
3. Frontend uploads directly to object storage.
4. Frontend saves `storageKey`, media type, caption, and sort order through the owner media endpoint.

Allowed image formats: JPEG, PNG, WebP. The backend must validate MIME type, size, ownership, and storage key before accepting metadata.

## Integration Rules

- CORS allows the configured frontend origins and required authorization/idempotency headers.
- Never trust `customerId`, `ownerId`, roles, listing status, review verification, or totals supplied by the client.
- Return field validation errors using JSON property names used in this contract.
- Publish OpenAPI docs at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` in non-production environments.
- Seed development accounts for each role; never enable demo authentication in production.
- Notify customers after owner status changes and owners after new enquiries. Notification delivery may be asynchronous, but the API write must succeed independently.

## Frontend Integration Order

1. Authentication and `/auth/me`
2. Public hall search and detail
3. Customer enquiry creation and history
4. Owner listing, enquiries, and calendar
5. Admin approval and moderation
6. Media uploads, reviews, and notifications
