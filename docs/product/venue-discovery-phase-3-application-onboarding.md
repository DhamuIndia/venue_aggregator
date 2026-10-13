# Venue discovery Phase 3: application-managed Overture drafts

Status: implemented locally on `codex/application-venue-onboarding`. No production
activation or deployment has occurred. Venue owners do not need an invitation,
account, ownership claim or OTP. The application enters the source facts;
administrators review the resulting private drafts.

## Implemented workflow

1. Prepare a bounded regional catalog from a pinned Overture Places release.
2. Open **Admin dashboard → Application onboarding** and browse or search it.
3. Select up to 20 records and preview their eligibility and possible duplicates.
4. Create the eligible records as ownerless `APPLICATION` listings in `DRAFT`.
5. Review the created drafts and their missing information in the same panel.

There is no publication, owner invitation, outreach, payment or photo-import
action in this phase. Drafts are excluded from public hall endpoints and the
ordinary venue approval queue. A database constraint prevents an application
draft from being changed to an approved listing through existing flows.

## Source and coverage evaluation

The implementation uses the official
[Overture Places schema](https://docs.overturemaps.org/schema/reference/places/place/)
and [DuckDB retrieval approach](https://docs.overturemaps.org/getting-data/duckdb/).
Retrieval happens outside web requests; the server reads a prepared JSON file once
at startup. This needs no additional server.

The 5 October 2026 Chennai evaluation used release `2026-09-23.1`, bounding box
`[80.18, 12.92, 80.32, 13.17]`, and confidence at least 0.8. It produced:

- 21 `event_venue` records, with no repeated source IDs.
- Address, city and postcode present in all 21.
- Area missing in all 21; phone missing in one; website missing in four.
- Operating status unknown in all 21.
- Microsoft and Overture provenance, explicitly licensed under CDLA Permissive 2.0.

The sample includes recognizable marriage halls such as Aasha Grand Mahal and
MCC Marriage Hall, but also questionable classifications, including a hotel and
a conference-alert service. This is a candidate feed, not a verified venue
inventory or a measurement of complete Chennai coverage. Admin review remains
necessary. No information is inferred to fill missing fields.

The two supported source categories are `event_venue` and
`exhibition_and_trade_fair_venue`. Labels such as banquet hall and wedding venue
are not substituted for those source categories.

## Licensing and provenance

Each draft retains the Overture place UUID, pinned release, exact catalog hash,
category, source website, operating status, source dataset/license/record IDs,
import time and importing administrator. A nullable field-level source record
ID is valid; the place UUID is the required stable import key.

The source-license mapping follows
[Overture attribution](https://docs.overturemaps.org/attribution/#places).
Unknown providers or inconsistent licenses are rejected. Overture-derived
provenance specifically requires an explicit CDLA license; it has no inferred
fallback.

The complete CDLA agreement is bundled at
`scripts/discovery/licenses/CDLA-Permissive-2.0.txt` and must travel with a shared
CDLA catalog. Only that agreement is bundled. Catalogs containing Foursquare
data additionally require the Apache 2.0 license and Foursquare NOTICE materials
before distribution; other source-specific obligations must also be supplied.
The evaluated Chennai catalog contains only CDLA-licensed provenance.

Google discovery remains separate: Google previews are not copied into these
drafts, and no Google photos or reviews are imported. Neutral photo placeholders
remain until VenueMart has independently permitted media.

## Import decisions and preservation

- Existing source UUID: return `ALREADY_IMPORTED` and its original hall ID.
- Same normalized name and city, or an existing hall within 75 metres:
  return `POSSIBLE_DUPLICATE` for review; do not merge or overwrite.
- Confidence below 0.8 or missing confidence: return `INCOMPLETE`.
- Known temporary/permanent closure or unrecognized status: block draft creation.
- Unknown operating status: allow a private draft and flag it for verification.
- Missing address, city, area, phone or website: preserve null and flag the gap.
- Photos, capacity, pricing and availability are not invented.

Each create transaction includes the hall, provenance and audit event. A
transaction-scoped PostgreSQL advisory lock serializes Overture imports, and
unique source/hall constraints prevent repeated or concurrent source imports.
Duplicate checks against simultaneous ordinary owner-created listings remain
best-effort; the source UUID guarantee does not depend on those checks.

V41 marks every existing hall as `OWNER`, retaining its existing owner/location
requirements. Only an `APPLICATION`/`DRAFT` listing may have no owner and missing
location fields. No synthetic owner is assigned. Existing vendors, customers,
bookings, media and notification jobs are not altered by import.

## Configuration and regional catalog preparation

Default configuration is disabled:

    OVERTURE_ONBOARDING_ENABLED=false
    OVERTURE_CATALOG_PATH=
    OVERTURE_MAX_BATCH_SIZE=20

The extractor is `scripts/discovery/overture_catalog.py`; its dependency is pinned
in `scripts/discovery/requirements.txt`. Example preparation from the repository
root, in a separate local Python virtual environment:

    python3 -m venv /private/tmp/venuemart-overture-tools
    /private/tmp/venuemart-overture-tools/bin/pip install -r scripts/discovery/requirements.txt
    /private/tmp/venuemart-overture-tools/bin/python3 scripts/discovery/overture_catalog.py \
      --release 2026-09-23.1 --city Chennai \
      --bbox 80.18 12.92 80.32 13.17 --max-records 100 \
      --min-confidence 0.8 --output /private/tmp/venuemart-overture-chennai.json

The extractor fixes the official public bucket path, validates the release and
bounding box, caps each axis at 0.5 degrees, caps records at 500 and output at
5 MiB, and bounds query time and DuckDB memory/spill. It refuses an existing output
unless `--overwrite` is explicitly given. The printed report includes rejected
record reasons and whether the sample was truncated. Oversized source facts are
rejected rather than silently shortened to fit hall columns.

The actual evaluated catalog remains outside Git at
`/private/tmp/venuemart-overture-chennai-20261005-licensed.json`. It is a temporary
local artifact, not a configured production source.

For a separately authorized deployment on the existing server, the optional
`infra/hetzner/docker-compose.overture.yml` mounts
`infra/hetzner/overture/` read-only at `/app/overture`. Prepare `catalog.json`
and all applicable license materials there before adding the override. The
override sets only the catalog path; the feature flag remains disabled unless
explicitly enabled. Do not upload real catalogs into Git.

The catalog is immutable for the running process. Replacing the file requires a
backend restart to load and validate a new snapshot. Missing/invalid files leave
the feature unready with a generic admin error. SHA-256 version checks reject
stale create requests with HTTP 409.

## API and access

All routes require a currently active admin or super-admin user and matching
active administrator profile, not just a claimed token role. Responses are
`Cache-Control: no-store`; source data is not placed in browser storage.

Base: `/api/v1/admin/overture-onboarding`.

- `GET /settings`: enabled/readiness, release, catalog hash and counts.
- `GET /catalog`: bounded pagination and local text search.
- `POST /preview`: selected source IDs and per-record decisions.
- `POST /import`: catalog hash and selected source IDs, with created/skipped results.
- `GET /drafts`: private imported drafts, provenance and missing fields.

There are no live provider calls in these endpoints. The existing Google adapter
is neither used nor needed by Overture onboarding.

## Verification

Local checks cover strict catalog parsing, source/license rules, nullable facts,
closed/low-confidence records, authorization, stale hashes, moderation isolation,
repeat and concurrent imports, and transaction rollback. The real 21-record
Chennai catalog passes the runtime parser.

The opt-in PostgreSQL test requires a fresh local database whose name begins
`venuemart_phase3_test_`, passed through `OVERTURE_TEST_DB`. It first migrates to
V40 and seeds an existing owner-managed approved hall with media, then starts the
application and applies V41. It verifies existing data survives, new drafts are
private and ownerless, concurrent creation returns one draft, and an audit failure
rolls back both hall and provenance. Google calls are mocked and never used;
WhatsApp sending/webhooks remain disabled.

Frontend client tests, type checking and the production build passed. Thirteen
mocked browser checks covered disabled mode, preview/create, duplicates, unknown
facts, conflicts, loading/error/empty states, attribution and 390px mobile layout,
with no external requests or page errors.

Final backend verification: 439 tests, 437 passed and two unrelated opt-in
integration tests skipped. The Overture PostgreSQL tests and the real catalog
check ran successfully. Extractor verification: 16 offline tests passed.
Frontend verification: eight Overture and seven discovery tests passed; final
type checking and production build passed with 24 generated routes.
Both base and optional-catalog Compose configurations validate. The disposable
test database was removed afterward; existing local databases were not changed.

## Next phase

Add admin draft editing and factual review, permitted venue media, and an explicit
publication/enquiry workflow for application-managed listings. Publication must
not imply verified availability, a partnership, booking authority or payment
collection. Source refresh must not overwrite administrator-verified edits.
