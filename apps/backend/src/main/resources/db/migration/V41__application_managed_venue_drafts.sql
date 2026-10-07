-- Existing venues remain OWNER listings with their ownership and publication rules intact.
alter table halls add column listing_origin varchar(30) not null default 'OWNER';
alter table halls alter column owner_user_id drop not null;
alter table halls alter column owner_name drop not null;
alter table halls alter column city drop not null;
alter table halls alter column area drop not null;

alter table halls add constraint ck_halls_listing_origin check (listing_origin in ('OWNER', 'APPLICATION'));
alter table halls add constraint ck_halls_listing_ownership check (
    (listing_origin = 'OWNER' and owner_user_id is not null and owner_name is not null and city is not null and area is not null)
    or (listing_origin = 'APPLICATION' and status = 'DRAFT' and owner_user_id is null and owner_name is null)
);

create table venue_overture_imports (
    id bigserial primary key,
    source_id uuid not null unique,
    hall_id bigint not null unique references halls(id),
    provider varchar(30) not null default 'OVERTURE_PLACES',
    catalog_version varchar(64) not null,
    release varchar(32) not null,
    category varchar(50) not null,
    source_website varchar(2048),
    source_operating_status varchar(40),
    sources jsonb not null,
    created_by_admin bigint not null references admins(id),
    imported_at timestamptz not null default now(),
    constraint ck_venue_overture_provider check (provider = 'OVERTURE_PLACES'),
    constraint ck_venue_overture_version check (catalog_version ~ '^[a-f0-9]{64}$'),
    constraint ck_venue_overture_release check (release ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[0-9]{1,2}$'),
    constraint ck_venue_overture_category check (category in ('event_venue', 'exhibition_and_trade_fair_venue')),
    constraint ck_venue_overture_sources check (jsonb_typeof(sources) = 'array' and jsonb_array_length(sources) > 0)
);
create index ix_venue_overture_imported_at on venue_overture_imports(imported_at desc, id desc);
