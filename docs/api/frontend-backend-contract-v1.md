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

Use optimistic locking (`version` field or `If-Match`) for enquiry status and listing edits. Return `409` when another actor already changed the resource.

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
| `POST` | `/payments/razorpay/webhook` | Process signed payment events |

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
