alter table reviews
    add column if not exists moderation_status varchar(30),
    add column if not exists report_reason varchar(500),
    add column if not exists moderation_reason varchar(500),
    add column if not exists moderated_by_admin_id bigint references admins(id),
    add column if not exists moderated_at timestamptz;

update reviews
set moderation_status = 'PUBLISHED'
where moderation_status is null;

alter table reviews
    alter column moderation_status set default 'PENDING',
    alter column moderation_status set not null;
