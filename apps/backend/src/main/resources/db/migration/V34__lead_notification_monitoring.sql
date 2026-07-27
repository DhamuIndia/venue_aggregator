create table lead_notification_evaluations (
    id bigserial primary key,
    vendor_lead_id bigint not null references vendor_leads(id) on delete cascade,
    vendor_id bigint not null references vendors(id) on delete cascade,
    requirement_id bigint not null references customer_requirements(id) on delete cascade,
    notification_job_id bigint references lead_notification_jobs(id) on delete set null,
    channel varchar(30) not null,
    notification_type varchar(50) not null,
    outcome varchar(60) not null,
    subscribed_at_evaluation boolean not null,
    skip_reason varchar(500),
    evaluated_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_lead_notification_evaluations_delivery
        unique (vendor_lead_id, channel, notification_type)
);

insert into lead_notification_evaluations (
    vendor_lead_id,
    vendor_id,
    requirement_id,
    notification_job_id,
    channel,
    notification_type,
    outcome,
    subscribed_at_evaluation,
    skip_reason,
    evaluated_at,
    updated_at
)
select
    vendor_lead.id,
    vendor_lead.vendor_id,
    vendor_lead.requirement_id,
    notification_job.id,
    'WHATSAPP',
    'LEAD_MATCHED',
    case
        when notification_job.id is not null then 'QUEUED'
        else 'LEGACY_NOT_RECORDED'
    end,
    notification_job.id is not null,
    case
        when notification_job.id is null
            then 'Notification decision predates Phase 6 monitoring'
        else null
    end,
    coalesce(notification_job.queued_at, vendor_lead.created_at, now()),
    now()
from vendor_leads vendor_lead
left join lead_notification_jobs notification_job
    on notification_job.vendor_lead_id = vendor_lead.id
   and notification_job.channel = 'WHATSAPP'
   and notification_job.notification_type = 'LEAD_MATCHED'
where vendor_lead.requirement_id is not null
on conflict (vendor_lead_id, channel, notification_type) do nothing;

create index idx_lead_notification_evaluations_requirement
    on lead_notification_evaluations(requirement_id, evaluated_at desc);

create index idx_lead_notification_jobs_admin_monitoring
    on lead_notification_jobs(requirement_id, status, queued_at desc);
