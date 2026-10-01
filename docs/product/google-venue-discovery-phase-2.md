# Venue discovery Phase 2 admin workflow

Phase 2 adds an admin screen and backend workflow for finding venue prospects,
reviewing them, and keeping search history. Discovery prospects remain separate
from public halls. This phase never publishes a listing, imports photos, contacts
an owner, sends WhatsApp messages, or changes a vendor record.

## Admin workflow

Open **Admin → Venue discovery**. When discovery is enabled, administrators can
search an Indian city and optional area for wedding halls, banquet halls,
convention centers, or party halls. A search reads one bounded page from Google's
Text Search API and records each returned Place ID as a prospect. Shortlisting
is an internal review decision, not publication or owner verification.

Search results show a transient name, address, business status, Google Maps link,
and supplied third-party attribution. Saved prospects and historical searches
retain IDs and VenueMart workflow metadata only. Use **Load preview** to request
fresh display content for a saved prospect; opening a history entry or changing a
review status does not call Google.

The review workflow permits `DISCOVERED`, `SHORTLISTED`, `REJECTED`, and `CLOSED`.
It cannot overwrite `INVITED`, `CLAIMED`, `OPTED_OUT`, or `DUPLICATE` records, or a
prospect already linked to a hall. Each update supplies the expected current
status, acquires a database row lock, and records an audit event. Stale updates
return HTTP 409 so another administrator's decision is not silently overwritten.

## Configuration and costs

Both switches default to false. No secret is sent to the browser.

| Variable | Default | Purpose |
| --- | --- | --- |
| `VENUE_DISCOVERY_ENABLED` | `false` | Enables admin workflow reads and review changes |
| `VENUE_DISCOVERY_LIVE_API_ENABLED` | `false` | Separately permits live Google search and previews |
| `VENUE_DISCOVERY_GOOGLE_API_KEY` | empty | Server-side Places API credential |
| `VENUE_DISCOVERY_MAX_RESULTS` | `10` | One search page, clamped to 1–20 results |
| `VENUE_DISCOVERY_DAILY_REQUEST_LIMIT` | `100` | Shared search plus preview request cap, clamped to 0–10000 |
| `VENUE_DISCOVERY_TIMEOUT_SECONDS` | `10` | Connection/read timeout, clamped to 1–30 seconds |

The daily budget uses an atomic PostgreSQL reservation and resets at midnight
UTC. It survives restarts and is shared across administrators and application
instances. Failures consume a reservation because the provider may have received
the request. There are no automatic retries. A zero budget blocks live calls.
This is a request-count cap, not a rupee spending guarantee; configure Google
Cloud quotas and billing alerts before authorizing live use.

The selected name/address fields use billable Places SKUs, not an ID-only search.
Google's [Text Search field masks](https://developers.google.com/maps/documentation/places/web-service/text-search#fieldmask)
and [Place Details documentation](https://developers.google.com/maps/documentation/places/web-service/place-details)
describe the fields and billing categories. Search is intentionally limited to a
single page; it is not an exhaustive list of venues in an area.

## API contract

All routes are under `/api/v1/admin/venue-discovery`. Requests require a currently
active user with `ADMIN` or `SUPER_ADMIN` role and an active matching admin profile.
Responses are `Cache-Control: no-store`. With the feature disabled, only settings
remain available; enabling workflow access alone does not enable Google calls.

| Method and path | Purpose |
| --- | --- |
| `GET /settings` | Read feature readiness, request budget, and supported venue types |
| `POST /search` | Search with `{city, area, venueType}`; return run, prospects, transient previews |
| `GET /runs?page=0&size=20` | Read stored search history |
| `GET /runs/{id}` | Read one run and its current prospect statuses |
| `GET /candidates?page=0&size=20&status=SHORTLISTED` | Read prospects with optional status filter |
| `POST /candidates/{id}/preview` | Explicitly request a fresh, potentially billable Google preview |
| `PATCH /candidates/{id}` | Review with `{status, expectedStatus}` |

Page sizes are limited to 50. Search city and area fields accept up to 120
characters; the UI uses a conservative 100-character limit. Unknown venue-type
values and invalid review payloads are rejected. Google failures and provider
response bodies are not persisted or exposed as exception details.

## Storage and provider content

V40 adds the daily request counter and listing indexes; it does not alter existing
hall, customer, vendor, or media records. Search persistence receives Place IDs
only, never preview objects. PostgreSQL uniqueness and conflict-safe insertion
prevent repeated or concurrent searches from duplicating a Place ID. Existing
review decisions survive rediscovery.

Provider display content is held only in the current UI session and response, not
in browser storage, application tables, or audit values. Preview cards distinguish
Google content from internal workflow controls and show attribution. No photos,
reviews, ratings, phone numbers, or email addresses are requested in this phase.
Follow Google's [Places policies and attribution rules](https://developers.google.com/maps/documentation/places/web-service/policies)
and keep the application's public terms/privacy notices compliant before live use.

Searches run synchronously with separate transactions for start, completion, and
failure. An abrupt process stop can leave a run marked `RUNNING`; this phase has
no background retry or crash-recovery worker. Such a run must not be interpreted
as an automatically queued future Google request.

## Verification and rollout

Backend unit/security tests mock all provider calls. The opt-in PostgreSQL test
accepts only a local database URL whose name begins `venuemart_phase2_test_`.
Create a fresh disposable database before each run; do not point it at a shared
development or production database. The test exercises migrations, deduplication,
review conflicts, failure history, durable concurrent quota reservations, and
unchanged hall/vendor/notification counts.

```sh
cd apps/backend
mvn test
# Optional, with a newly created local database and the local development DB user:
VENUE_DISCOVERY_TEST_DB=jdbc:postgresql://localhost:5432/venuemart_phase2_test_example \
  mvn -Dtest=VenueDiscoveryWorkflowIntegrationTest test
```

```sh
cd apps/frontend
npm run typecheck
npm run test:discovery
npm run build
```

Before live activation, obtain explicit approval for Google billing and requests,
configure a restricted server key, confirm Google Cloud quotas, and validate a
small controlled search. Deployment and feature activation are separate approvals.
Owner invitations, claim verification, photo consent, and publication belong to
later phases and are not enabled by these switches.

## Local verification on 30 September 2026

- Clean backend suite: 382 tests, zero failures or errors, two opt-in tests skipped.
- The new PostgreSQL workflow test passed separately in a fresh local database;
  all 40 migrations and JPA validation succeeded. The disposable database was
  removed after verification.
- Frontend client tests: seven passed. TypeScript checking and the production
  build passed, including all 24 static pages. The build used a local API URL.
- Mocked browser checks covered disabled and live-disabled modes, search,
  shortlist updates, filtering, explicit previews, history, browser-storage
  safety, and a 390-pixel mobile viewport without horizontal overflow.
- Production Compose configuration validated without starting services.

These checks made no live Google API calls. Real API credentials, billing,
provider responses, and deployment remain deliberately unverified.
