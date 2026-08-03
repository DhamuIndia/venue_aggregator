create table if not exists reviews (
    id bigserial primary key,
    booking_id bigint not null references bookings(id),
    enquiry_id bigint not null references enquiries(id),
    hall_id bigint not null references halls(id),
    customer_user_id bigint not null references users(id),
    rating integer not null,
    comment varchar(500) not null,
    verified_service boolean not null default true,
    active boolean not null default true,
    moderation_status varchar(30) not null default 'PENDING',
    report_reason varchar(500),
    moderation_reason varchar(500),
    moderated_by_admin_id bigint references admins(id),
    moderated_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_review_per_enquiry unique(enquiry_id)
);

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
