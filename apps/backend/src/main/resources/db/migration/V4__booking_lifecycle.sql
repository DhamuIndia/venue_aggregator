alter table bookings
    add column if not exists amount numeric(12, 2),
    add column if not exists payment_status varchar(40) not null default 'NOT_STARTED',
    add column if not exists confirmed_at timestamptz,
    add column if not exists completed_at timestamptz,
    add column if not exists cancelled_at timestamptz;

update bookings
set status = 'REQUESTED'
where status = 'ENQUIRY';

update bookings
set payment_status = 'ADVANCE_PENDING',
    confirmed_at = coalesce(confirmed_at, created_at)
where status = 'CONFIRMED';

update bookings
set completed_at = coalesce(completed_at, updated_at)
where status = 'COMPLETED';

update bookings
set amount = vendor_hall_details.amount
from vendor_hall_details
where bookings.hall_id = vendor_hall_details.id
  and bookings.amount is null;

create index if not exists ix_bookings_customer_user_id_event_date
    on bookings(customer_user_id, event_date desc);

create index if not exists ix_bookings_hall_id_event_date
    on bookings(hall_id, event_date desc);

create index if not exists ix_bookings_status
    on bookings(status);
