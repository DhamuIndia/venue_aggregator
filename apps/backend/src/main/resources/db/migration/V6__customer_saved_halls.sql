create table if not exists customer_saved_halls (
    id bigserial primary key,
    customer_user_id bigint not null references users(id) on delete cascade,
    hall_id bigint not null references halls(id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (customer_user_id, hall_id)
);

create index if not exists ix_customer_saved_halls_customer_created_at
    on customer_saved_halls(customer_user_id, created_at desc);

create index if not exists ix_customer_saved_halls_hall_id
    on customer_saved_halls(hall_id);
