create table if not exists audit_events (
    id bigserial primary key,
    actor_user_id bigint references users(id) on delete set null,
    actor_role varchar(50),
    action varchar(80) not null,
    entity_type varchar(80) not null,
    entity_id varchar(80),
    summary varchar(500),
    old_values text,
    new_values text,
    metadata text,
    request_ip varchar(80),
    user_agent varchar(500),
    created_at timestamptz not null default now()
);

create index if not exists ix_audit_events_action_created_at
    on audit_events(action, created_at desc);

create index if not exists ix_audit_events_entity
    on audit_events(entity_type, entity_id);

create index if not exists ix_audit_events_actor_user_id
    on audit_events(actor_user_id);
