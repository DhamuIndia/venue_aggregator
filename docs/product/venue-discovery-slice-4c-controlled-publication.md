# VenueMart Slice 4C controlled publication and availability requests

Slice 4C lets active VenueMart admins publish reviewed application-managed venues
and handle customer availability requests. It does not register venue owners,
confirm dates, create bookings, collect payments or send WhatsApp messages.

The local feature branch is `codex/venue-publication`, created from `develop`
commit `ba3e64a` with the separate Slice 4B dependency commit `8ed35b2`. Slice 4B
must land in `develop` before the 4C change is merged. This work does not authorize
a push, pull request, merge, deployment or publication of real venues.

## Publication workflow

The Application onboarding area has a separate publication management list for
both private drafts and live listings. It remains possible to unpublish a live
listing even though it is no longer in the Created drafts list.

Before publication, the backend checks the current venue facts, duplicate matches
and media together. Required facts must be verified, the operating status must be
open, the duplicate assessment must still match the current complete match set,
and any possible matches must be explicitly assessed as distinct. At least one
approved photo and an approved cover are required. Prices and availability are
not inferred from missing values.

This slice blocks publication if an approved photo is marked LICENSED_IMAGE.
Slice 4B records private licensing evidence but has no separate safe public image
credit field. Such photos remain private until a later credit workflow supports
their public reuse. Team-owned and business-permitted photos still require the
recorded permission and admin review; approval is an attestation, not automated
proof of rights.

Publish requires the current publication, factual-review and media versions,
plus an admin reason. Stale versions return HTTP 409; the UI does not automatically
resubmit them. Publication and withdrawal record the admin, time, versions and
reason in an immutable history, with a transactional audit event.
The management response shows the latest 100 history entries; older entries remain
in the database. A duplicate assessment above its bounded match limits blocks
publication but does not block management reads or withdrawal. The factual editor
retains its strict limit and cannot treat a partial match set as verified.

Published facts and photos are read-only. Unpublish returns the listing to a
private draft so facts or photos can be corrected. Republishing creates a new
publication version and reruns eligibility checks. Owner-managed hall approval,
ownership and ordinary media moderation remain separate.

## Public presentation and photo access

Application-managed listings show **Request availability** and **Request pricing**.
They explain that the VenueMart team will check the request and that no date is
confirmed. They do not show owner verification, guaranteed free dates, a live
availability calendar or an online payment action. Public source attribution and
the Overture release are retained; private factual notes, photo permission
evidence, storage keys and credentials are not returned.

The frontend does not fill missing application venue data with mock prices,
photos, amenities or reviews. In API mode, failed hall reads fail visibly rather
than substitute demonstration data. Explicit mock mode remains available for
development.

Approved photos stay in the private bucket on the existing MinIO server. A
bounded backend public-image endpoint checks the active listing and publication
version before returning a selected approved image. It does not make the bucket
public or copy images to existing `hall_media`. Responses are uncacheable and the
frontend avoids image optimization for these URLs. Unpublish revokes subsequent
image requests; it cannot erase an image a viewer has already downloaded.
Hall detail pages are rendered on each request rather than statically cached, so
a fresh page load also respects withdrawal.

Public search, detail, saved listings and new enquiry creation use the explicit
publication gate, not merely the hall's APPROVED status. Application listings
cannot obtain a misleading empty calendar from the owner availability API.

## Availability request handling

Customers use the existing authenticated enquiry form. Application-managed
requests persist with `routingTarget=VENUEMART`, the publication version they
were submitted against, and status NEW. Creation and withdrawal serialize on the
venue row so a request does not bypass a completed withdrawal.

The team queue is separate from owner enquiries. An active database-backed admin
can advance a request from NEW to CONTACTED, then CLOSED, with a customer-visible
team response and an internal reason. The update is version-checked. It cannot
confirm or complete a booking, reopen a closed request or invoke the owner booking
workflow. Customer enquiry views label the VenueMart team and its response.

Only in-app notifications are produced: customers receive team wording and active
VenueMart admins receive queue notifications. No venue owner is invented or
notified. Existing owner and vendor enquiry records keep their original route.
Customers can still view their request and the team can finish handling it after
the venue is unpublished.
Failed application enquiry submissions do not generate local success receipts or
cache customer notes/contact data as fallback team enquiries. Old locally stored
team records are ignored rather than used to manufacture confirmation.

Audit events retain status, version and actor changes without duplicating contact
details or free-text customer responses. The latest internal team-update reason
is stored on the enquiry but omitted from customer responses. These fields are
not a full conversation or CRM replacement.

## API and database

Publication management uses `/api/v1/admin/overture-onboarding/publications`:

- `GET /` returns a paginated draft/live management list.
- `GET /{hallId}` returns current versions, readiness blockers and history.
- `POST /{hallId}/publish` accepts publication/review/media versions and a reason.
- `POST /{hallId}/unpublish` accepts the publication version and a reason.

Approved public photo bytes use
`GET /api/v1/halls/{hallId}/application-photos/{photoId}?publicationVersion=N`.

Customer submission remains `POST /api/v1/public/enquiries`. The team queue uses
`GET /api/v1/admin/application-venue-enquiries` with pagination and an optional
NEW/CONTACTED/CLOSED status filter, and `PUT /{enquiryId}` with expectedVersion,
status, responseMessage and reason.

V44 adds explicit publication state, selected photos and immutable publication
history, while retaining ownerless application listing constraints. V45 adds
enquiry routing and team-response fields with owner defaults for existing data.
Database guards prevent application enquiry confirmation and application venue
booking creation.

## Configuration and rollout

`OVERTURE_PUBLICATION_ENABLED=false` is the default in backend and deployment
examples. Onboarding and private-media flags remain independently disabled by
default. No production configuration or storage policy was changed during
implementation. No new server is required.

A later rollout requires explicit authorization to deploy migrations, provision
the private bucket on the existing server if necessary, enable the relevant
flags, and publish a specific reviewed venue. Check the factual and photo rights
evidence, fresh duplicate assessment, public listing wording, anonymous image
access/revocation and team enquiry routing with synthetic data first. Preserve
the normal database and existing public bucket. Do not enable WhatsApp sending
or accept payments as part of this rollout.

## Verification

Verified locally on 6 October 2026:

- Backend `mvn clean test`: 700 reported, 697 passed, no failures or errors,
  3 environment-gated skips. These skips are the regional catalog smoke,
  WhatsApp forwarding repository and requirement opt-in integration checks.
- The backend run included 21 passing real PostgreSQL/MinIO workflow checks:
  2 import, 5 factual review, 7 private media and 7 publication/enquiry tests.
  The latter cover concurrent publication, audit rollback, stale versions,
  team routing, booking rejection, private photo revocation, the kill switch and
  withdrawal after both duplicate-match and candidate-query overflow.
- Frontend: 61 tests passed, TypeScript passed and the production build passed.
  Hall detail remains a dynamic route. The ordinary build was restored after
  the temporary local QA API build; no environment file was changed.
- Eleven isolated headless-browser scenarios passed using synthetic API data,
  with no page errors or external requests. Desktop and 390-pixel mobile views
  were inspected for publication, the team queue, public detail and discovery.
  These checks included submission failures, preserved conflict forms and a
  fresh-load 404 after withdrawal.

The four disposable workflow databases and two private photo-test buckets were
removed after verification. Their synthetic fixtures are reproducible from the
tests. The local QA servers were stopped; the normal application database,
existing media buckets, CRM containers and production were not modified.

These checks do not establish real photo rights, verify a production migration
or authorize live publication. The feature remains disabled by default and has
not been pushed or deployed.
