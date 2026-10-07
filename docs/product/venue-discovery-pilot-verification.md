# Venue discovery pilot verification

## Scope and release status

Slice 4D is merged into `develop` through [PR 94](https://github.com/DhamuIndia/venue_aggregator/pull/94), at commit `326d02be9a7dde9490c98ab4d343a5e796dfc47b`. Verification on 7 October 2026 uses local PostgreSQL and separate private MinIO buckets on the existing machine. Real venues remain unpublished, and this verification does not authorize a production deployment or rollout flags.

The local verification branch is `codex/venue-pilot-verification`. Remote `main` remains at `2712a6a9d3feeb92acb443179af64756b9679e62`. No CRM configuration, production data, payment behavior or outbound WhatsApp message is changed.

## Local verification results

The continuous backend pilot check passed. One synthetic venue completed catalog preview and import, factual review, a licensed synthetic photo and reviewed credits, publication, customer enquiry, team handling and withdrawal. It uses Spring MVC with authenticated test users, PostgreSQL and private MinIO storage; it is not a browser login test. Synthetic evidence validates application behavior, not the rights or accuracy of a real third-party venue or image.

The merged backend regression suite and targeted pilot rerun produced 798 test results: 796 passed, zero failures or errors and two optional skips. All 29 existing Overture database workflow checks ran, as did the new continuous pilot and the regenerated Chennai catalog parser check. The remaining skips are the separate Google discovery workflow and WhatsApp forwarding repository integration checks.

The pilot verified that pending credits block publication, approved credits expose only public fields and the image bytes match their private canonical JPEG. The customer request progressed through NEW, CONTACTED and CLOSED. Withdrawal hid the listing from detail, search and saved venues, revoked the old photo URL and rejected a second enquiry while preserving the first request. Private image access, original source facts, credit revisions and publication history remained intact.

The final synthetic venue was DRAFT and UNPUBLISHED at publication version 2, with one closed enquiry, two credit revisions, one publication-credit snapshot, two publication history records and 11 audit events. There were zero bookings, payments, subscriptions, WhatsApp jobs or sending attempts. The pilot asserts zero interactions with mocked Places and WhatsApp clients, so neither external path is exercised.

Frontend verification passed all 78 existing Node tests and TypeScript checking. The catalog preparation suite passed all 16 Python tests. Existing isolated browser verification for Slice 4D is documented in [the Slice 4D report](venue-discovery-slice-4d-photo-credits.md); it uses mocked API responses rather than this continuous backend journey.

All eight disposable databases and six private test buckets created for these checks were removed afterwards. Their generated synthetic contents can be recreated from the guarded integration tests. The normal application database, existing media bucket and CRM services were retained.

## Chennai candidate catalog

The bounded extractor regenerated 21 candidates from release `2026-09-23.1`, with a minimum confidence of 0.8 and bounding box west 80.18, south 12.92, east 80.32, north 13.17. The list is not truncated and has no repeated source IDs or normalization rejections. [Overture lists this release as current on 7 October 2026](https://docs.overturemaps.org/release-calendar/).

Every candidate has unknown operating status and lacks an area field. One lacks a phone number and four lack a website. Capacity and photos are not exported by this catalog workflow. The list also contains names that need venue-type screening, such as a gas agency and conference-alert listings, and two records named Trust Conference Hall that require duplicate assessment. Source confidence is not verification of current business facts.

The local catalog is `/private/tmp/venuemart-overture-chennai-pilot-20261007.json`, generated at 10:23 UTC on 7 October 2026. Its SHA-256 is `f6067f378fa7b83cbaea1171f23494866c6bcdfbd7d53f2a28671f468ba18c63`. A companion license file is `/private/tmp/venuemart-overture-chennai-pilot-20261007-CDLA-Permissive-2.0.txt`. These are temporary files, not production configuration.

All retained provenance is labeled CDLA Permissive 2.0, from Microsoft and Overture-derived fields. Microsoft's mapping matches [Overture's Places attribution documentation](https://docs.overturemaps.org/attribution/#places); derived fields retain their explicit source license. This does not grant rights to photographs found elsewhere.

## Nonblocking customer experience findings

Rejected saves do not falsely advance the saved state, but `SaveHallButton` currently gives no error explanation after a failed request. A stale page can therefore appear unresponsive after venue withdrawal or an API failure.

Customer enquiry reads fall back to owner-only local records on API errors. VenueMart team requests are deliberately excluded from that fallback, preventing false local confirmations, but a temporary outage can appear as an empty list or missing enquiry. Explicit retryable error messages would improve both cases before broader rollout.

## Gates before a real venue pilot

1. Select a small set of actual venue candidates and screen venue type and duplicate matches. Import only private drafts using the reviewed catalog version.
2. Independently verify the required name, address, city, area, business phone, coordinates, open status and capacity. Record accurate review evidence; do not infer missing values from source confidence or venue names.
3. Obtain permitted venue photos. Record immutable usage evidence, review the images, approve complete matching credits where required and choose a cover. Do not import Google Maps photos or treat public availability as permission.
4. Confirm an active admin, customer test account and a named team member who will handle enquiries. The pilot remains enquiry-only, without owner onboarding, bookings, payments or outbound notification messages.
5. Obtain explicit approval for the production release, backup, additive migration and scoped flags. Use a separate private media bucket on the existing server; no new server is required. Keep publication off until all selected drafts pass their readiness checks.
6. Publish each approved pilot venue explicitly, verify desktop and mobile listing and credits, submit one authorized customer enquiry, process it through NEW, CONTACTED and CLOSED, then demonstrate withdrawal revoking the photo URL and blocking new enquiries. Retain the audit evidence and agree on the rollback action before widening publication.

The code defaults for onboarding, private draft media, publication and licensed-photo publication remain off. Passing local checks does not enable these flags or establish readiness of the 21 real candidates.

## Repeating the local pilot

Provision a fresh local PostgreSQL database named `venuemart_pilot_test_<unique_suffix>` and a separate private MinIO bucket named `venuemart-pilot-test-<unique-suffix>`. The test refuses a used public database schema and restricts its database URL to localhost or 127.0.0.1. It uses the repository's local development credentials and existing MinIO service at localhost port 9000; never substitute production credentials or an application database.

From `apps/backend`, set `OVERTURE_PILOT_TEST_DB` to the disposable JDBC URL and `OVERTURE_PILOT_TEST_BUCKET` to that private bucket, then run `mvn -Dtest=OverturePilotWorkflowIntegrationTest test`. The test supplies its own synthetic catalog and keeps live discovery, WhatsApp sending and webhooks disabled in that test context. Remove only those exact disposable fixtures after the run; the candidate catalog and companion license may be retained for private review.
