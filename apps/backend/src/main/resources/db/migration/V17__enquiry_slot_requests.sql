create table if not exists enquiry_slot_requests (
    id bigserial primary key,
    enquiry_id bigint not null references enquiries(id) on delete cascade,
    event_date date not null,
    slot_type varchar(30) not null,
    created_at timestamptz not null default now(),
    constraint uq_enquiry_slot_requests_enquiry_date_slot
        unique (enquiry_id, event_date, slot_type)
);

insert into enquiry_slot_requests (enquiry_id, event_date, slot_type)
select id, event_date, slot_type
from enquiries
where event_date is not null
  and slot_type is not null
on conflict do nothing;

create index if not exists ix_enquiry_slot_requests_enquiry_id
    on enquiry_slot_requests(enquiry_id);

create index if not exists ix_enquiry_slot_requests_date_slot
    on enquiry_slot_requests(event_date, slot_type);
