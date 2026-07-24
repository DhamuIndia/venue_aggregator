create table customer_requirements (
    id bigserial primary key,
    customer_user_id bigint not null references users(id),
    event_type varchar(120) not null,
    event_date date not null,
    location varchar(255) not null,
    city varchar(120),
    pincode varchar(16),
    budget_min numeric(12, 2),
    budget_max numeric(12, 2),
    guest_count integer,
    details varchar(2000),
    preferred_contact_channel varchar(30) not null default 'IN_APP',
    share_contact_details boolean not null default false,
    status varchar(40) not null default 'OPEN',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_customer_requirements_budget_min_non_negative
        check (budget_min is null or budget_min >= 0),
    constraint ck_customer_requirements_budget_max_non_negative
        check (budget_max is null or budget_max >= 0),
    constraint ck_customer_requirements_budget_range
        check (budget_min is null or budget_max is null or budget_min <= budget_max),
    constraint ck_customer_requirements_guest_count_positive
        check (guest_count is null or guest_count > 0)
);

create table requirement_services (
    requirement_id bigint not null references customer_requirements(id) on delete cascade,
    category_id bigint not null references vendor_categories(id),
    primary key (requirement_id, category_id)
);

alter table vendor_leads
    add column requirement_id bigint;

alter table vendor_leads
    add constraint fk_vendor_leads_requirement
        foreign key (requirement_id)
        references customer_requirements(id)
        on delete set null;

create index idx_customer_requirements_customer_created_at
    on customer_requirements(customer_user_id, created_at desc);

create index idx_customer_requirements_status_event_date
    on customer_requirements(status, event_date);

create index idx_requirement_services_category
    on requirement_services(category_id, requirement_id);

create index idx_vendor_leads_requirement
    on vendor_leads(requirement_id);

create unique index uq_vendor_leads_requirement_vendor
    on vendor_leads(requirement_id, vendor_id)
    where requirement_id is not null;
