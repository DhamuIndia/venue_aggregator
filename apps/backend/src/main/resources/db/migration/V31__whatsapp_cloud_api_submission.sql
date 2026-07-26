alter table lead_notification_jobs
    add column provider_message_id varchar(255);

alter table lead_notification_jobs
    add column processing_started_at timestamptz;

alter table lead_notification_jobs
    add column submitted_at timestamptz;

alter table lead_notification_jobs
    add column send_failed_at timestamptz;

alter table lead_notification_jobs
    add column cancelled_at timestamptz;

create unique index uq_lead_notification_jobs_provider_message
    on lead_notification_jobs(provider_message_id)
    where provider_message_id is not null;

create index idx_lead_notification_jobs_dispatch_status
    on lead_notification_jobs(status, queued_at, id);
