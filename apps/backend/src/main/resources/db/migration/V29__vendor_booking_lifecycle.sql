alter table vendor_service_bookings
    add column advance_amount numeric(12, 2),
    add column balance_amount numeric(12, 2),
    add column advance_due_date date,
    add column started_at timestamptz,
    add column completed_at timestamptz,
    add column cancelled_at timestamptz,
    add column cancelled_by varchar(30),
    add column cancellation_reason varchar(1000),
    add column refundable_amount numeric(12, 2) not null default 0,
    add column reminder_7d_sent_at timestamptz,
    add column reminder_1d_sent_at timestamptz;

update vendor_service_bookings
set advance_amount = round(amount * 0.20, 2),
    balance_amount = amount - round(amount * 0.20, 2),
    advance_due_date = least(event_date, (confirmed_at at time zone 'UTC')::date + 2),
    payment_status = case
        when payment_status = 'NOT_STARTED' then 'ADVANCE_PENDING'
        else payment_status
    end;

alter table vendor_service_bookings
    alter column advance_amount set not null,
    alter column balance_amount set not null,
    alter column advance_due_date set not null,
    add constraint ck_vendor_service_bookings_advance_non_negative check (advance_amount >= 0),
    add constraint ck_vendor_service_bookings_balance_non_negative check (balance_amount >= 0),
    add constraint ck_vendor_service_bookings_refundable_non_negative check (refundable_amount >= 0);

create table vendor_booking_payments (
    id bigserial primary key,
    vendor_service_booking_id bigint not null references vendor_service_bookings(id),
    payment_type varchar(30) not null,
    amount numeric(12, 2) not null,
    currency varchar(3) not null default 'INR',
    status varchar(30) not null,
    provider varchar(30) not null default 'RAZORPAY',
    provider_order_id varchar(100) not null,
    provider_payment_id varchar(100),
    provider_refund_id varchar(100),
    provider_signature varchar(255),
    receipt_number varchar(50),
    paid_at timestamptz,
    refund_status varchar(30) not null default 'NOT_REQUESTED',
    refund_amount numeric(12, 2) not null default 0,
    refund_requested_at timestamptz,
    refunded_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_vendor_booking_payments_provider_order unique (provider_order_id),
    constraint uq_vendor_booking_payments_provider_payment unique (provider_payment_id),
    constraint uq_vendor_booking_payments_provider_refund unique (provider_refund_id),
    constraint uq_vendor_booking_payments_receipt unique (receipt_number),
    constraint ck_vendor_booking_payments_amount_positive check (amount > 0),
    constraint ck_vendor_booking_payments_refund_non_negative check (refund_amount >= 0)
);

create index idx_vendor_booking_payments_booking_created
    on vendor_booking_payments(vendor_service_booking_id, created_at desc);

create table vendor_booking_timeline (
    id bigserial primary key,
    vendor_service_booking_id bigint not null references vendor_service_bookings(id),
    event_type varchar(50) not null,
    from_status varchar(30),
    to_status varchar(30),
    actor_user_id bigint references users(id),
    actor_role varchar(30) not null,
    message varchar(500) not null,
    created_at timestamptz not null default now()
);

create index idx_vendor_booking_timeline_booking_created
    on vendor_booking_timeline(vendor_service_booking_id, created_at desc);
