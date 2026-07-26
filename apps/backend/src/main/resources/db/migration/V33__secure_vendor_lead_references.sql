alter table vendor_leads
    add column public_reference varchar(25);

update vendor_leads
set public_reference = 'LEAD-' || upper(substr(
        md5(random()::text || clock_timestamp()::text || id::text),
        1,
        20))
where public_reference is null;

alter table vendor_leads
    alter column public_reference set not null;

create unique index uq_vendor_leads_public_reference
    on vendor_leads (public_reference);

alter table lead_notification_jobs
    add column lead_reference varchar(25);

update lead_notification_jobs notification_job
set lead_reference = vendor_lead.public_reference
from vendor_leads vendor_lead
where notification_job.vendor_lead_id = vendor_lead.id
  and notification_job.lead_reference is null;

alter table lead_notification_jobs
    alter column lead_reference set not null;
