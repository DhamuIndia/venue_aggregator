-- Shared, durable budget for both search and explicit preview requests. No provider content.
create table venue_discovery_api_usage (
    usage_date date primary key,
    request_count integer not null check (request_count >= 0)
);

create index idx_venue_discovery_runs_created on venue_discovery_runs(created_at desc, id desc);
create index idx_venue_discovery_candidates_discovered
    on venue_discovery_candidates(discovered_at desc, id desc);
