alter table vendor_leads
    add column contact_details_released boolean not null default false;

create table vendor_service_bookings (
    id bigserial primary key,
    vendor_quote_id bigint not null references vendor_quotes(id),
    vendor_lead_id bigint not null references vendor_leads(id),
    requirement_id bigint references customer_requirements(id),
    vendor_id bigint not null references vendors(id),
    customer_user_id bigint not null references users(id),
    service varchar(255) not null,
    package_name varchar(160) not null,
    event_type varchar(120) not null,
    event_date date not null,
    location varchar(255) not null,
    amount numeric(12, 2) not null,
    status varchar(30) not null default 'CONFIRMED',
    payment_status varchar(30) not null default 'NOT_STARTED',
    confirmed_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_vendor_service_bookings_quote unique (vendor_quote_id),
    constraint uq_vendor_service_bookings_lead unique (vendor_lead_id),
    constraint ck_vendor_service_bookings_amount_positive check (amount > 0)
);

create unique index uq_vendor_service_bookings_requirement
    on vendor_service_bookings(requirement_id)
    where requirement_id is not null;

create index idx_vendor_service_bookings_customer_event
    on vendor_service_bookings(customer_user_id, event_date desc);

create index idx_vendor_service_bookings_vendor_event
    on vendor_service_bookings(vendor_id, event_date desc);
