alter table vendor_leads
    add column decline_reason varchar(1000);

create table vendor_quotes (
    id bigserial primary key,
    vendor_lead_id bigint not null references vendor_leads(id) on delete cascade,
    vendor_id bigint not null references vendors(id),
    amount numeric(12, 2) not null,
    package_name varchar(160) not null,
    service_description varchar(2000) not null,
    additional_charges numeric(12, 2) not null default 0,
    additional_charges_description varchar(1000),
    notes varchar(2000),
    valid_until date not null,
    status varchar(30) not null default 'SENT',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_vendor_quotes_lead unique (vendor_lead_id),
    constraint ck_vendor_quotes_amount_positive check (amount > 0),
    constraint ck_vendor_quotes_additional_charges_non_negative check (additional_charges >= 0)
);

create table vendor_quote_inclusions (
    quote_id bigint not null references vendor_quotes(id) on delete cascade,
    inclusion varchar(300) not null,
    sort_order integer not null,
    primary key (quote_id, sort_order)
);

create index idx_vendor_quotes_vendor_updated_at
    on vendor_quotes(vendor_id, updated_at desc);

create index idx_vendor_quotes_valid_until
    on vendor_quotes(valid_until);
