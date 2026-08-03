create table if not exists notifications (
    id bigserial primary key,
    recipient_user_id bigint not null references users(id) on delete cascade,
    type varchar(30) not null,
    title varchar(120) not null,
    message varchar(500) not null,
    action_href varchar(250),
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists ix_notifications_recipient_created_at
    on notifications(recipient_user_id, created_at desc);

create index if not exists ix_notifications_recipient_unread
    on notifications(recipient_user_id)
    where read_at is null;
