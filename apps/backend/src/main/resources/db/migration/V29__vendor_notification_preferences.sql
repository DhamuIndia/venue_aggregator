create table vendor_notification_preferences (
    id bigserial primary key,
    vendor_id bigint not null references vendors(id) on delete cascade,
    whatsapp_lead_notifications_enabled boolean not null default false,
    whatsapp_lead_notifications_paused boolean not null default false,
    whatsapp_number varchar(20),
    whatsapp_consented_at timestamptz,
    whatsapp_consent_source varchar(40),
    whatsapp_opted_out_at timestamptz,
    whatsapp_paused_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_vendor_notification_preferences_vendor unique (vendor_id),
    constraint ck_vendor_notification_preferences_enabled_consent
        check (
            not whatsapp_lead_notifications_enabled
            or (
                whatsapp_number is not null
                and whatsapp_consented_at is not null
                and whatsapp_consent_source is not null
                and whatsapp_opted_out_at is null
            )
        ),
    constraint ck_vendor_notification_preferences_pause
        check (
            not whatsapp_lead_notifications_paused
            or (
                whatsapp_lead_notifications_enabled
                and whatsapp_paused_at is not null
            )
        )
);

create index idx_vendor_notification_preferences_whatsapp_eligible
    on vendor_notification_preferences(vendor_id)
    where whatsapp_lead_notifications_enabled
      and not whatsapp_lead_notifications_paused;
