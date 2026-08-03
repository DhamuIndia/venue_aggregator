create table lead_notification_jobs (
    id bigserial primary key,
    vendor_lead_id bigint not null references vendor_leads(id) on delete cascade,
    vendor_id bigint not null references vendors(id) on delete cascade,
    requirement_id bigint not null references customer_requirements(id) on delete cascade,
    channel varchar(30) not null,
    notification_type varchar(50) not null,
    status varchar(40) not null default 'QUEUED',
    destination varchar(20) not null,
    template_key varchar(80) not null,
    template_language varchar(10) not null,
    vendor_name varchar(180) not null,
    service_text varchar(500) not null,
    event_type_text varchar(120) not null,
    event_date_text varchar(80) not null,
    location_text varchar(255) not null,
    budget_text varchar(120) not null,
    queued_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_lead_notification_jobs_delivery
        unique (vendor_lead_id, channel, notification_type)
);

create index idx_lead_notification_jobs_pending
    on lead_notification_jobs(status, queued_at, id)
    where status = 'QUEUED';

create index idx_lead_notification_jobs_requirement
    on lead_notification_jobs(requirement_id, queued_at desc);
