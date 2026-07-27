alter table lead_notification_jobs
    add column sent_at timestamptz;

alter table lead_notification_jobs
    add column delivered_at timestamptz;

alter table lead_notification_jobs
    add column read_at timestamptz;

alter table lead_notification_jobs
    add column failed_at timestamptz;

alter table lead_notification_jobs
    add column failure_code integer;

alter table lead_notification_jobs
    add column failure_title varchar(255);

alter table lead_notification_jobs
    add column failure_reason varchar(1000);

alter table lead_notification_jobs
    add column failure_temporary boolean;

alter table lead_notification_jobs
    add column attempt_count integer not null default 0;

alter table lead_notification_jobs
    add column next_retry_at timestamptz;

alter table lead_notification_jobs
    add column status_updated_at timestamptz;

create table whatsapp_notification_attempts (
    id bigserial primary key,
    job_id bigint not null references lead_notification_jobs(id) on delete cascade,
    attempt_number integer not null,
    status varchar(40) not null,
    provider_message_id varchar(255),
    requested_at timestamptz not null,
    sent_at timestamptz,
    delivered_at timestamptz,
    read_at timestamptz,
    failed_at timestamptz,
    cancelled_at timestamptz,
    status_updated_at timestamptz,
    failure_code integer,
    failure_title varchar(255),
    failure_reason varchar(1000),
    failure_temporary boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_whatsapp_notification_attempt_number
        check (attempt_number > 0),
    constraint uq_whatsapp_notification_attempt_number
        unique (job_id, attempt_number)
);

create unique index uq_whatsapp_notification_attempt_provider_message
    on whatsapp_notification_attempts(provider_message_id)
    where provider_message_id is not null;

create index idx_whatsapp_notification_attempt_job
    on whatsapp_notification_attempts(job_id, attempt_number desc);

insert into whatsapp_notification_attempts (
    job_id,
    attempt_number,
    status,
    provider_message_id,
    requested_at,
    sent_at,
    failed_at,
    status_updated_at,
    failure_title,
    failure_reason,
    failure_temporary
)
select
    id,
    1,
    case
        when status = 'SUBMITTED' then 'SENT'
        when status = 'SEND_FAILED' then 'FAILED'
        else status
    end,
    provider_message_id,
    coalesce(processing_started_at, queued_at),
    case
        when status = 'SUBMITTED' then coalesce(submitted_at, updated_at)
        else null
    end,
    case
        when status = 'SEND_FAILED' then coalesce(send_failed_at, updated_at)
        else null
    end,
    updated_at,
    case
        when status = 'SEND_FAILED' then 'Legacy submission failure'
        else null
    end,
    case
        when status = 'SEND_FAILED'
            then 'Failure recorded before delivery tracking was enabled'
        else null
    end,
    case
        when status = 'SEND_FAILED' then false
        else null
    end
from lead_notification_jobs
where status in ('PROCESSING', 'SUBMITTED', 'SEND_FAILED')
   or provider_message_id is not null
on conflict (job_id, attempt_number) do nothing;

update lead_notification_jobs
set
    status = case
        when status = 'SUBMITTED' then 'SENT'
        when status = 'SEND_FAILED' then 'FAILED'
        else status
    end,
    sent_at = case
        when status = 'SUBMITTED' then coalesce(submitted_at, updated_at)
        else sent_at
    end,
    failed_at = case
        when status = 'SEND_FAILED' then coalesce(send_failed_at, updated_at)
        else failed_at
    end,
    failure_title = case
        when status = 'SEND_FAILED' then 'Legacy submission failure'
        else failure_title
    end,
    failure_reason = case
        when status = 'SEND_FAILED'
            then 'Failure recorded before delivery tracking was enabled'
        else failure_reason
    end,
    failure_temporary = case
        when status = 'SEND_FAILED' then false
        else failure_temporary
    end,
    attempt_count = case
        when status in ('PROCESSING', 'SUBMITTED', 'SEND_FAILED')
             or provider_message_id is not null
            then greatest(attempt_count, 1)
        else attempt_count
    end,
    status_updated_at = updated_at
where status in ('PROCESSING', 'SUBMITTED', 'SEND_FAILED')
   or provider_message_id is not null;

create index idx_lead_notification_jobs_retry_ready
    on lead_notification_jobs(next_retry_at, id)
    where status = 'FAILED'
      and failure_temporary = true
      and next_retry_at is not null;
