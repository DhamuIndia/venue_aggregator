alter table enquiries
    add column if not exists customer_user_id bigint references users(id),
    add column if not exists hall_id bigint references vendor_hall_details(id),
    add column if not exists event_type varchar(120),
    add column if not exists guest_count integer,
    add column if not exists slot_type varchar(30),
    add column if not exists owner_response_message text,
    add column if not exists responded_at timestamptz,
    add column if not exists version bigint not null default 0;

alter table enquiries
    alter column status set default 'PENDING_OWNER_RESPONSE';

create index if not exists ix_enquiries_customer_user_id_created_at
    on enquiries(customer_user_id, created_at desc);

create index if not exists ix_enquiries_hall_id_created_at
    on enquiries(hall_id, created_at desc);

create index if not exists ix_enquiries_status
    on enquiries(status);

alter table bookings
    add column if not exists hall_id bigint references vendor_hall_details(id),
    add column if not exists enquiry_id bigint unique references enquiries(id);

create unique index if not exists uq_bookings_confirmed_hall_date_slot
    on bookings(hall_id, event_date, slot_type)
    where status = 'CONFIRMED' and hall_id is not null;
