# VenueMart Slice 4B private venue photos and media review

Slice 4B adds photo upload and review for application-managed Overture venue
drafts. Admins record the source and reuse permission, review a normalized image,
and choose an approved cover and display order. All assets remain private.
Photo approval does not publish the venue, create an owner account, establish
availability or enable bookings, payments or WhatsApp messages.

The branch is `codex/venue-draft-media`, started from merged `develop` commit
`ba3e64a`. Building this slice does not authorize deployment or storage changes
in production.

## Admin workflow

Open **Admin dashboard → Application onboarding → Created drafts → Review
private photos**. Photo review has its own dialog and version counter, separate
from factual review in Slice 4A. Saving or reloading photos does not replace an
open facts form.

Upload a JPEG or PNG file with a caption, source details and permission evidence.
Record one of these source and rights combinations:

| Source | Rights basis | Additional evidence |
| --- | --- | --- |
| Team photo | Team owned | How the team obtained and can use the image |
| Business provided | Business permission | Who supplied it and what reuse was authorized |
| Licensed image | Open license | Source reference, license name and supporting evidence |

The uploader explicitly confirms the recorded rights. This is an admin
attestation, not automatic proof that the permission or license is valid.
Reviewers must assess both the image and its recorded permission before approval.
Source and rights records are immutable after upload; corrections require
archiving the record and uploading a replacement.

New photos are pending. An admin can approve or reject a pending photo with a
reason. The reviewer identity and time are recorded. Reviewed photos are not
silently rewritten by subsequent review actions. Archive a photo with a reason
when it should no longer be used, retaining its earlier review and upload history.

Only approved, non-archived photos can be the private cover or appear in the
approved display order. Arrangement must account for every approved photo.
Archiving a cover clears the private cover selection. None of these actions
updates the public hall cover or the existing `hall_media` records.

## Image validation and limits

The first slice accepts JPEG and PNG only. WebP, GIF, SVG, arbitrary file types
and URL imports are not supported. There is no Google Maps photo ingestion or
automatic download from a submitted source reference.

The backend checks actual image bytes, declared type and image dimensions before
full decoding. It normalizes images to JPEG and removes original metadata rather
than storing the uploaded file unchanged. Transparent backgrounds become white.
JPEG EXIF orientation is applied before the metadata is removed. Corrupt or
truncated containers are rejected. Admins should preview the normalized result
before approving it.

Limits are eight MiB per input, four MiB per normalized output, 6,000 pixels on
either side and 16 million total pixels. A bounded decoder limits concurrent
image processing. Multipart file and request limits apply before the service
accepts a file.

Each draft permits up to 20 active photos, 64 MiB of retained normalized images
and 100 upload records. Archived images still count toward retained storage and
lifetime records. Archiving is not a storage deletion or an unlimited quota reset.
A later retention workflow must explicitly address permanent removal and evidence
retention; this slice does not add a background deletion job.

## Private storage and previews

The existing MinIO `venue-media` bucket is configured for anonymous downloads.
Putting files under a random prefix in that bucket would not make them private.
Slice 4B therefore uses a separate private bucket on the same storage server.
It does not change policies or contents of the public bucket and needs no new
server.

Server-only storage requests use Signature Version 4. Before storing or reading
an image, the adapter checks that bucket policy and access-control settings do
not grant public access. Uploads use a private object ACL, and reads also check
the object ACL. Unavailable or ambiguous privacy checks fail closed. The adapter
does not follow redirects or return storage credentials, object keys or storage
URLs to the browser.

MinIO returns a synthetic owner-only ACL rather than real canonical owner IDs.
The adapter accepts only that narrow response shape with a MinIO server header
or no server header (as in current MinIO), continues to check bucket policy, and
additionally requires unsigned bucket and object GET access denial. It does not
interpret arbitrary empty-ID ACLs as private. MinIO documents
policies as its alternative to ACL operations; its [ACL handler source](https://github.com/minio/minio/blob/master/cmd/acl-handlers.go)
shows the synthetic response. See also [MinIO S3 compatibility](https://docs.min.io/aistor/developers/s3-api-compatibility/).

Image bytes are delivered only through the active-admin authenticated API with
private/no-store and nosniff response headers. The browser loads a preview only
when requested, using an in-memory Blob URL, and clears that preview on expiry,
reload, archive, close or account change. It does not persist photos, credentials
or preview URLs in local or session storage, or pass them through public image
optimization.

This storage design follows the documented separation between bucket policies
and object ACLs and the documented signing procedure. See [Amazon S3 access
controls](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
and [Signature Version 4 header signing](https://docs.aws.amazon.com/AmazonS3/latest/developerguide/sig-v4-header-based-auth.html).

## Authorization and transaction safety

Every operation checks the active user role and matching active admin profile
in the database. A token claiming an admin role is insufficient. The hall must
be an imported, ownerless `APPLICATION` listing in `DRAFT`, with factual-review
state. Owner-managed venues, ordinary media moderation and public endpoints do
not gain access to this workflow.

Photo operations use an independent media version and row locking. A stale
version returns HTTP 409. The editor retains selected files, evidence and reasons
until the admin explicitly discards them or reloads the latest record; it does
not automatically resubmit a write.

Photo metadata, version changes and audit recording commit together. If an upload
transaction rolls back, the newly created storage object is removed through
compensation. Storage is external to PostgreSQL, so operators must still consider
process interruption and failed cleanup when planning retention and reconciliation.
Archiving retains the stored object and immutable provenance, but removes preview
access through this workflow.

## API and database

The base is `/api/v1/admin/overture-onboarding/drafts/{hallId}/media`.

- `GET /` returns photo metadata, private arrangement, version and limits.
- `POST /upload` accepts an authenticated multipart file and JSON metadata.
- `PUT /{mediaId}/review` approves or rejects a pending photo with a reason.
- `PUT /arrangement` selects the approved private cover and full approved order.
- `POST /{mediaId}/archive` archives a photo with a reason.
- `GET /{mediaId}/content` returns a normalized image to an active admin only.

Requests cannot set ownership, public status, object keys, storage URLs or public
cover URLs. Gallery metadata contains no downloadable public URLs. V43 stores
separate draft-photo records and media state; the previous source-facts snapshot
and review version are unchanged.

## Configuration and rollout

`OVERTURE_ONBOARDING_ENABLED` and `OVERTURE_DRAFT_MEDIA_ENABLED` remain false by default.
The private bucket defaults to `venue-draft-media-private` and is configured with
`OVERTURE_DRAFT_MEDIA_BUCKET`. It must differ from `S3_BUCKET`. The adapter uses the
server-side `S3_ENDPOINT` and existing storage credentials, region and path-style
configuration, not the browser presign endpoint or public media URL.

Before a separately authorized rollout, an operator must provision the private
bucket on the existing storage server, verify policy and ACL access, grant the
backend only its required storage permissions and test anonymous denial. Neither
the application nor this implementation task automatically creates a production
bucket, edits a production policy or activates the feature. Existing public
media must remain untouched.

Public publication and enquiry routing belong to Slice 4C, with fresh duplicate,
factual and media eligibility checks. An approved photo alone is not permission
to publish an application-managed venue.

## Verification status

The final backend run passed on 6 October 2026: 618 tests reported,
608 passed and 10 opt-in integration/catalog cases skipped. This includes seven
real PostgreSQL/private MinIO scenarios, covering migration backfill, canonical
private upload, concurrent version checks, review/order, audit rollback and object
compensation, archival, authorization and existing-business isolation. Frontend discovery,
Overture and private-media client/form tests passed (42 tests), and type checking
passed. The production frontend build passed. Fourteen codec cases also passed
with the new processor classes in a network-isolated Java 21 Alpine runtime.

The real PostgreSQL/MinIO runs exposed MinIO's synthetic ACL format and omitted
server header, now covered by strict shape and anonymous-GET-denial regressions. Twelve
mocked desktop/mobile browser scenarios passed with no page errors, external
requests or unexpected API calls. They covered multipart metadata, review,
arrangement, archival, conflict/discard and busy-close behavior, token refresh,
account/logout cleanup, authenticated Blob previews and 60-second revocation.
The desktop and 390px mobile screenshots were visually reviewed.

These checks do not authorize rollout. No production migration, bucket provisioning, feature
activation or deployment has occurred.

The disposable verification resources were removed after the passing run:
databases `venuemart_phase4b_test_20261005_root`,
`venuemart_phase4a_test_20261005_4b` and `venuemart_phase3_test_20261005_4b`, and
bucket `venuemart-phase4b-test-20261005-root`. The normal application database and
public media bucket were not changed. A rerun requires fresh isolated fixtures.
