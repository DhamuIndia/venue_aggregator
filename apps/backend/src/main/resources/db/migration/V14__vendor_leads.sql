create table if not exists vendor_leads (
    id bigserial primary key,
    vendor_id bigint not null references vendors(id),
    customer_user_id bigint not null references users(id),
    customer_name varchar(255),
    customer_phone varchar(50),
    customer_email varchar(255),
    service varchar(255),
    event_type varchar(120),
    event_date date,
    location varchar(255),
    budget numeric(12, 2),
    notes varchar(1000),
    status varchar(40) not null default 'NEW',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_vendor_leads_vendor_created_at
    on vendor_leads(vendor_id, created_at desc);

create index if not exists idx_vendor_leads_customer_created_at
    on vendor_leads(customer_user_id, created_at desc);
