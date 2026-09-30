create table venue_discovery_runs (
    id bigserial primary key,
    source varchar(40) not null default 'GOOGLE_PLACES',
    status varchar(40) not null default 'CREATED',
    search_mode varchar(30) not null,
    city varchar(120) not null,
    area varchar(120),
    requested_place_types varchar(500) not null,
    center_latitude double precision,
    center_longitude double precision,
    radius_meters integer,
    created_by_admin_id bigint not null references admins(id) on delete restrict,
    started_at timestamptz,
    completed_at timestamptz,
    failure_reason varchar(1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_venue_discovery_runs_source
        check (source in ('GOOGLE_PLACES')),
    constraint ck_venue_discovery_runs_status
        check (status in ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    constraint ck_venue_discovery_runs_search_mode
        check (search_mode in ('NEARBY', 'TEXT')),
    constraint ck_venue_discovery_runs_city
        check (btrim(city) <> ''),
    constraint ck_venue_discovery_runs_requested_types
        check (btrim(requested_place_types) <> ''),
    constraint ck_venue_discovery_runs_latitude
        check (center_latitude is null or center_latitude between -90 and 90),
    constraint ck_venue_discovery_runs_longitude
        check (center_longitude is null or center_longitude between -180 and 180),
    constraint ck_venue_discovery_runs_radius
        check (radius_meters is null or radius_meters between 1 and 50000),
    constraint ck_venue_discovery_runs_nearby_location
        check (
            search_mode = 'TEXT'
            or (
                center_latitude is not null
                and center_longitude is not null
                and radius_meters is not null
            )
        ),
    constraint ck_venue_discovery_runs_completion
        check (completed_at is null or started_at is null or completed_at >= started_at)
);

create index idx_venue_discovery_runs_status_created
    on venue_discovery_runs(status, created_at desc);

create table venue_discovery_candidates (
    id bigserial primary key,
    source varchar(40) not null default 'GOOGLE_PLACES',
    source_place_id varchar(255) not null,
    status varchar(40) not null default 'DISCOVERED',
    linked_hall_id bigint references halls(id) on delete restrict,
    duplicate_of_candidate_id bigint references venue_discovery_candidates(id) on delete restrict,
    internal_notes text,
    discovered_at timestamptz not null default now(),
    source_checked_at timestamptz not null default now(),
    status_changed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_venue_discovery_candidates_source_place
        unique (source, source_place_id),
    constraint ck_venue_discovery_candidates_source
        check (source in ('GOOGLE_PLACES')),
    constraint ck_venue_discovery_candidates_place_id
        check (btrim(source_place_id) <> ''),
    constraint ck_venue_discovery_candidates_status
        check (status in (
            'DISCOVERED',
            'SHORTLISTED',
            'INVITED',
            'CLAIMED',
            'DUPLICATE',
            'OPTED_OUT',
            'REJECTED',
            'CLOSED'
        )),
    constraint ck_venue_discovery_candidates_not_self_duplicate
        check (duplicate_of_candidate_id is null or duplicate_of_candidate_id <> id),
    constraint ck_venue_discovery_candidates_duplicate_state
        check ((status = 'DUPLICATE') = (duplicate_of_candidate_id is not null)),
    constraint ck_venue_discovery_candidates_claimed_hall
        check (status <> 'CLAIMED' or linked_hall_id is not null)
);

create unique index uq_venue_discovery_candidates_linked_hall
    on venue_discovery_candidates(linked_hall_id)
    where linked_hall_id is not null;

create index idx_venue_discovery_candidates_status
    on venue_discovery_candidates(status, status_changed_at desc);

create table venue_discovery_run_candidates (
    id bigserial primary key,
    discovery_run_id bigint not null references venue_discovery_runs(id) on delete restrict,
    candidate_id bigint not null references venue_discovery_candidates(id) on delete restrict,
    observed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    constraint uq_venue_discovery_run_candidates
        unique (discovery_run_id, candidate_id)
);

create index idx_venue_discovery_run_candidates_candidate
    on venue_discovery_run_candidates(candidate_id, observed_at desc);

create table venue_candidate_assignments (
    id bigserial primary key,
    candidate_id bigint not null references venue_discovery_candidates(id) on delete restrict,
    assigned_admin_id bigint not null references admins(id) on delete restrict,
    assigned_by_admin_id bigint not null references admins(id) on delete restrict,
    assigned_at timestamptz not null default now(),
    unassigned_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_venue_candidate_assignments_window
        check (unassigned_at is null or unassigned_at >= assigned_at)
);

create unique index uq_venue_candidate_assignments_active
    on venue_candidate_assignments(candidate_id)
    where unassigned_at is null;

create index idx_venue_candidate_assignments_admin
    on venue_candidate_assignments(assigned_admin_id, assigned_at desc);

create table venue_claims (
    id bigserial primary key,
    candidate_id bigint not null references venue_discovery_candidates(id) on delete restrict,
    token_hash varchar(64) not null unique,
    status varchar(40) not null default 'ISSUED',
    verification_method varchar(40) not null default 'PHONE_OTP',
    claimed_by_user_id bigint references users(id) on delete set null,
    created_by_admin_id bigint not null references admins(id) on delete restrict,
    expires_at timestamptz not null,
    verified_at timestamptz,
    consumed_at timestamptz,
    revoked_at timestamptz,
    consent_version varchar(80),
    consent_at timestamptz,
    consent_ip varchar(80),
    consent_user_agent varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_venue_claims_token_hash
        check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint ck_venue_claims_status
        check (status in ('ISSUED', 'VERIFIED', 'CONSUMED', 'EXPIRED', 'REVOKED', 'DISPUTED')),
    constraint ck_venue_claims_verification_method
        check (verification_method in ('PHONE_OTP', 'OWNER_ACCOUNT')),
    constraint ck_venue_claims_expiry
        check (expires_at > created_at)
);

create unique index uq_venue_claims_active_candidate
    on venue_claims(candidate_id)
    where status in ('ISSUED', 'VERIFIED');

create index idx_venue_claims_candidate_created
    on venue_claims(candidate_id, created_at desc);

create index idx_venue_claims_expiry
    on venue_claims(status, expires_at)
    where status in ('ISSUED', 'VERIFIED');

create table venue_outreach_events (
    id bigserial primary key,
    candidate_id bigint not null references venue_discovery_candidates(id) on delete restrict,
    channel varchar(30) not null,
    outcome varchar(40) not null,
    performed_by_admin_id bigint not null references admins(id) on delete restrict,
    notes text,
    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    constraint ck_venue_outreach_events_channel
        check (channel in ('PHONE', 'EMAIL', 'WHATSAPP', 'IN_PERSON', 'OTHER')),
    constraint ck_venue_outreach_events_outcome
        check (outcome in (
            'ATTEMPTED',
            'CONTACTED',
            'NO_RESPONSE',
            'INTERESTED',
            'DECLINED',
            'OPTED_OUT',
            'INVALID_CONTACT'
        ))
);

create index idx_venue_outreach_events_candidate
    on venue_outreach_events(candidate_id, occurred_at desc);
