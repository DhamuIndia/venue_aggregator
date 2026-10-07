# Slice 4D — reviewed photo credits

## Outcome and limits

Application-managed venues can prepare public credits for licensed photos, obtain an explicit admin review, and publish those credits alongside the corresponding images. Publication remains enquiry-only: this slice adds no owner onboarding, bookings, payment gateway, WhatsApp sends, automatic image acquisition, or Google Maps photo imports.

The new licensed-photo switch defaults to off. Building or merging this code does not authorize production deployment, migration, bucket-policy changes, enabling a flag, or publishing a real venue. Existing owner listings and team/business-permission photo publication keep their established paths.

## Admin workflow

1. Upload a permitted image into the existing separate private draft-media bucket. Record its original license and private usage evidence accurately; these remain immutable.
2. Review the image privately. For a licensed image, enter its title, creator, optional creator link, public source link, license, prior changes, and any source-provided attribution, copyright or disclaimer notices.
3. Save credits as a new pending revision. Saving a correction to approved credits invalidates the previous approval for future publication.
4. Review the latest pending revision with a reason and explicit confirmation of both usage rights and public attribution. The underlying photo must already be approved.
5. With separately authorized rollout flags enabled, use the existing explicit venue-publication action. Every approved licensed photo needs complete, latest-approved credits; partial publication is not permitted.
6. To correct a live image or credit, unpublish first, append a corrected revision, review again, and republish. Previous publication-credit snapshots remain immutable.

Credit records are bounded to 100 revisions per photo. Saving reserves the last slot for review; an exhausted record needs a replacement upload rather than an in-place rewrite. Gallery and credit versions are both checked. A stale request returns a conflict, keeps the form intact, and requires a deliberate reload; it is never automatically retried.

## Supported licenses and human judgment

The initial policy accepts only version-specific CC BY 4.0 and CC0 1.0 labels that match the immutable upload evidence. Unknown licenses, other versions, noncommercial/no-derivatives/share-alike variants and mismatched evidence remain blocked. This is an application acceptance policy, not a finding that a rights holder validly licensed the image.

CC BY 4.0 requires suitable credit, applicable supplied notices, license information, and disclosure of changes. Reviewers must check the original source and preserve relevant attribution parties and earlier modifications in the public notices. [Official CC BY 4.0 terms](https://creativecommons.org/licenses/by/4.0/legalcode.en).

CC0 permits broad reuse to the extent the dedication is effective, but does not eliminate every privacy, publicity, trademark or other third-party concern. VenueMart still requires source and creator metadata as a conservative product policy; that is not a claim that CC0 itself mandates attribution. [Official CC0 1.0 terms](https://creativecommons.org/publicdomain/zero/1.0/legalcode.en).

An admin must verify venue association, source authenticity, authority to grant rights, usage permission and any other applicable rights. Checkbox completion cannot provide legal certification. Source links are displayed, not fetched. Do not put secret URLs or personal/private permission details into public-credit fields.

## API and data boundary

Private admin routes, relative to `/api`, are:

- `PUT /v1/admin/overture-onboarding/drafts/{hallId}/media/{mediaId}/credits`
- `POST /v1/admin/overture-onboarding/drafts/{hallId}/media/{mediaId}/credits/review`

They require an active database admin, enabled private-onboarding/media features, an ownerless application draft and a retained licensed photo. Requests use strict JSON and both `expectedVersion` and `expectedCreditVersion`. Review approval additionally requires `rightsConfirmed` and `attributionConfirmed`.

V46 adds append-only `venue_overture_photo_credits` and `venue_overture_publication_photo_credits`. Credit saves/reviews increment the shared media version in the same transaction as the audit event. Database checks enforce sequential revisions, safe fields, matching original licenses, live-edit prohibition and exact publication snapshots, supplementing service checks. Historical migrations are not rewritten.

The additive public `applicationPhotos` array contains `photoId`, `url`, `requiresCredit` and a nullable public `credit`. The credit whitelist is title, creator/link, source link, derived license code/label/link, changes, processing notice and supplied notices. Private evidence, review reasons, admin identities and storage keys are excluded. Team/business photos do not require a credit object; owner responses retain their existing behavior.

Image cards, gallery tiles and fullscreen viewing present the relevant credit. Links stay outside image buttons and card-navigation links. Malformed required credit metadata fails closed on the client rather than showing an uncredited licensed image. Application-managed photo responses remain versioned, private-storage-backed and non-cacheable; withdrawal revokes their public endpoints.

## Rollout controls

`OVERTURE_LICENSED_PHOTO_PUBLICATION_ENABLED=false` adds a separate gate beneath existing onboarding, media and publication flags. It does not enable publication by itself. Turning it off hides licensed-photo application listings and their public photos; withdrawal remains available. Existing team/business-photo listings and owner listings are not disabled by this extra gate.

Before production rollout, obtain explicit approval, back up the database, apply the additive migration using the normal deployment process, verify the existing private bucket and exercise a small reviewed pilot. No new server is required. Unknown rights or missing credit data must remain private.

## Verification

Local verification on 6 October 2026 used synthetic, disposable PostgreSQL databases and separate private MinIO buckets, plus isolated browser fixtures:

- Backend: 797 tests, 794 passed, zero failures/errors, three optional environment-gated skips. All 29 Overture real-database workflow checks ran, including eight new photo-credit checks.
- Frontend: 78 tests passed; type checking and both fixture and ordinary production builds passed.
- Browser: 11 isolated headless checks passed, with no page errors, external browser requests or unexpected writes. Desktop and 390-pixel mobile screens were visually inspected. A small signed-in mobile-header spacing correction was included after the browser check detected overflow.
- Upgrade compatibility: an existing owner row and a pre-V46 team-photo publication were unchanged, and the existing team image endpoint still returned its exact canonical JPEG with the licensed-photo flag off.
- Safety: tested unknown/mismatched licenses, hidden/encoded controls, Java/SQL URL-validation parity, strict JSON, stale versions, audit rollback, latest-pending/rejected approval invalidation, direct SQL tampering, private-field exclusion, switch-off hiding, withdrawal and versioned republication.

The browser fixtures do not establish real photo rights or verify third-party source content. Local PostgreSQL/MinIO fixtures and preview servers were removed after verification; they can be recreated from the integration tests and their guarded environment variables. No production data, CRM configuration, real venue publication or outbound message was changed.

For local reruns, use `mvn test` in `apps/backend`, and `node --test tests/*.test.cjs` plus `npm run typecheck` / `npm run build` in `apps/frontend`. The real workflow tests require freshly created local databases named `venuemart_phase3_test_*` / `venuemart_phase4a_test_*` through `venuemart_phase4d_test_*` and separate private photo buckets named `venuemart-phase4b-test-*` through `venuemart-phase4d-test-*`. Set the corresponding `OVERTURE_*_TEST_DB` and photo `OVERTURE_*_TEST_BUCKET` variables; never point them at a normal application or production database.
