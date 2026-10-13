# Google-assisted venue discovery: Phase 1 foundation

Phase 1 introduces storage for an admin-only venue prospect workflow. It does not
call Google APIs, expose a public endpoint, create halls, send outreach, or change
existing hall and vendor records.

## Safety boundary

Discovery candidates and public halls are separate concepts:

```text
venue_discovery_candidates --future verified claim--> halls
```

Only a later verified claim flow may link a candidate to a hall. Public hall search
continues to return only approved records from `halls`.

The discovery tables intentionally do not contain Google-provided names, addresses,
phone numbers, ratings, reviews, photos, or photo references. They store the source
Place ID and VenueMart-owned workflow metadata. Later phases must retrieve display
content from the provider at use time and follow provider attribution and caching
rules.

## Tables

- `venue_discovery_runs`: controlled searches requested by an administrator.
- `venue_discovery_candidates`: unique source Place IDs and internal workflow state.
- `venue_discovery_run_candidates`: each run in which a unique candidate was observed.
- `venue_candidate_assignments`: assignment history with one active assignee.
- `venue_claims`: hashed, expiring claim credentials and consent evidence.
- `venue_outreach_events`: append-only operational contact outcomes without contact
  destinations.

## Candidate lifecycle

```text
DISCOVERED -> SHORTLISTED -> INVITED -> CLAIMED
     |             |           |
     +------> DUPLICATE         +--> OPTED_OUT
     +------> REJECTED
     +------> CLOSED
```

The exact transition policy will be implemented with the admin and claim services in
later phases. The database restricts values, requires a duplicate target for the
`DUPLICATE` state, and requires a linked hall for the `CLAIMED` state.

## Claim-token policy

Only a lowercase SHA-256 token hash is stored. Plaintext claim tokens must never be
persisted or logged. The database permits only one active (`ISSUED` or `VERIFIED`)
claim per candidate.

## Operational defaults

`VENUE_DISCOVERY_ENABLED` defaults to `false`. Phase 1 has no runtime integration,
but the flag establishes the fail-closed configuration expected by later phases.
