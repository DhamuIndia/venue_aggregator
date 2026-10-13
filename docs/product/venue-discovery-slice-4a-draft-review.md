# VenueMart Slice 4A draft editing and factual review

Slice 4A lets active administrators complete and verify application-managed
Overture venue drafts. Original imported facts remain available beside the
edited values. Factual review is internal: even a verified record remains a
private, ownerless `APPLICATION` listing with hall status `DRAFT`.

This work adds no owner registration, publication, permitted-photo workflow,
availability promises, booking changes, payment collection or WhatsApp changes.
The Overture feature remains disabled by default and requires no additional server.

## Admin workflow

Open **Admin dashboard → Application onboarding → Created drafts → Edit and
review**. The editor presents current values, original source values, their
origin and any field verification. Existing drafts can be reviewed while the
regional catalog file is unavailable; importing new venues still needs a valid
prepared catalog.

Admins can edit venue name, address, city, area, postcode, coordinates, phone,
website, operating status, capacity, description and eight amenity fields.
Amenities have three values: unknown, yes and no. Unknown is not silently
converted into a negative answer. Coordinates must be supplied together or both
left unknown. Source identifiers, licenses, ownership, public status, photos,
prices and payments are not editable through this endpoint.

Save partial work as **In review**. Check a supplied field as verified only when
there is supporting evidence, and describe that evidence in the review notes.
The server records the administrator identity and UTC verification time.
Unchanged verified fields retain their original verifier, timestamp and evidence.
Editing a field clears its old verification unless it is explicitly reverified
with new evidence. A known false amenity value can be verified; an unknown value
cannot.

## Original facts and verification

V42 stores a source-facts snapshot for every existing Overture import. Phase 3
had no editing workflow, so this backfill captures the hall's imported values
and the import's original website and operating status. New imports initialize
the same review state inside the original import transaction.

The snapshot is immutable, with a database trigger preventing changes. Source
UUID, release, catalog version, dataset/license provenance and the original
website/status remain unchanged. Admin website and status corrections are
stored separately as effective review values.

Each field is presented as source supplied, admin edited or missing. Origin and
verification answer different questions: a source-supplied value can be verified
by an admin without pretending that the admin supplied the original fact.

Verification is recorded for each field. The editor also shows missing and
unverified details, the last reviewing administrator and the last review time.
Audit events record changed field names, verified field names, review version
and decisions, without copying contact details or free-form evidence into the
general audit payload.

## Internal review states

`UNREVIEWED` and `IN_REVIEW` support partial work. `VERIFIED` means minimum venue
facts have been checked, not that the venue is public, partnered or bookable.
`DUPLICATE` records an internal duplicate finding and does not merge or delete
either listing.

Completing factual review requires name, address, city, area, phone, latitude,
longitude, capacity and an open operating status to be supplied and verified.
Website, postcode, description and amenities are optional for this checkpoint.
Missing photos or prices do not prevent factual review because their collection
and publication rules belong to later slices.

Known closures and unknown operating status cannot complete factual review.
All review states leave the underlying hall in `DRAFT`. Existing public endpoints
and ordinary venue moderation continue to exclude application-managed drafts.

## Duplicate review

The editor checks current facts against existing halls, excluding itself. The
match rules are the Phase 3 rules: normalized name and city, or coordinates within
75 metres. It displays matched IDs, names, city/area, origin, status and available
distance. A possible match is not proof that the venues are identical.

An admin may record that all listed matches are distinct, with a reason, or
confirm a duplicate. A decision must acknowledge every current matched hall ID;
it cannot silently omit a newly appearing match. Identity/location edits clear
the previous assessment. Save those edits first, then review the refreshed
matches. Unresolved matches prevent `VERIFIED` status. Confirmed duplicates use
`DUPLICATE` review status, with no changes to the matched venues.

The list shows the last saved review state. Opening a draft rechecks its current
matches: a new unresolved match or an outdated acknowledgement changes the
displayed state to **In review** without rewriting historical review metadata
or incrementing the version. Saving an outdated match acknowledgement returns
HTTP 409 and requires an explicit reload before reassessment.

The importer still blocks source candidates that may match existing halls.
This slice reviews matches on already created drafts; it does not add a catalog
import override or automatically merge records. A later publication workflow
must recheck duplicates and other eligibility rather than treating an old
verification as permanent approval.

## Saving and conflicts

Every save carries the last loaded review version. The server locks the review
and hall records, checks that version and increments it after a successful
transaction. Hall edits, review state and audit recording commit together. An
audit failure rolls back the edit.

A stale version returns HTTP 409. The editor retains unsaved values, shows the
conflict and offers an explicit reload/discard choice. It does not retry the
write automatically or report success for an unsuccessful response. Normal
token renewal should preserve the same account's open editor; logout or an
account change clears it.

Source and editor values are not saved into browser storage. Reads and writes
are uncached and require a current admin session. A claimed token role alone is
insufficient: the backend checks the active user role and matching active admin
profile on each operation.

## API and database changes

The existing base is `/api/v1/admin/overture-onboarding`.

- `GET /drafts/{hallId}` returns current/source facts, origin labels, field
  verification, version, missing details and current duplicate matches.
- `PUT /drafts/{hallId}` replaces the editable facts and internal review choices,
  with an `expectedVersion`, explicit verified field list and review evidence.

PUT requires every supported fact and amenity key; null clears an optional
value. Unknown keys, ownership/publication fields, invalid scalar types and
malformed values, duplicate JSON keys and extra trailing JSON are rejected.
The service accepts only imported ownerless
`APPLICATION`/`DRAFT` halls. Owner-managed and missing records are not editable.

V42 adds `venue_overture_draft_reviews`, its source snapshot, effective website
and status, version, verifications, duplicate assessment, notes and reviewer
metadata. It retains V41's ownership/public-status constraints. No production
database has been migrated as part of local implementation.

## Verification scope

The disposable PostgreSQL test first applies migrations through V41 and seeds an
existing owner-managed venue with media plus a Phase 3 imported draft. Application
startup applies V42. Tests exercise source snapshot backfill, review without a
catalog file, partial edits, false-versus-null amenities, verification invalidation,
version conflicts, concurrent saves, duplicate decisions, audit rollback and
existing owner/public access safeguards.

The opt-in test accepts only a fresh local database named
`venuemart_phase4a_test_*`, provided through `OVERTURE_REVIEW_TEST_DB`. Existing
local databases and production are not used. Google calls are mocked, and
WhatsApp sending and webhook processing remain disabled in the test application.

Validation on 5 October 2026: the complete backend suite ran 487 tests with 485
passing, no failures and two unrelated opt-in integration tests skipped. Both
Overture PostgreSQL suites ran, including V41-to-V42 backfill, concurrent saves,
audit rollback and a newly appearing duplicate after verification. The prepared
licensed regional catalog smoke test also ran. The two disposable databases were
removed after testing; existing databases and media were not changed.

All 24 frontend client/form tests passed (17 Overture and seven discovery tests),
as did TypeScript checking and the frontend production build (24 routes).
An isolated browser run passed 11 mocked workflow
cases on desktop and a 390-pixel mobile viewport, including source preservation,
tri-state amenities, verification evidence, duplicate decisions, conflict/error
reload confirmation, busy/disabled states, focus handling and same-account token
renewal. It made no external API calls or persistent business-data changes.

## Release and following work

The feature branch is `codex/venue-draft-review`, created from `develop` and
including the Phase 3 implementation as a local dependency. Phase 3 PR #90 must
land in `develop` before the 4A changes are merged there. Neither shared branch
is modified by this implementation task.

The next slice can add independently permitted venue photos and the media review
workflow. Public listing and enquiry routing need a separate explicit workflow;
factual verification alone must not enable bookings or payment collection.
