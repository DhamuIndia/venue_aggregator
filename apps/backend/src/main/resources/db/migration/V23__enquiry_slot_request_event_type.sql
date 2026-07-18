alter table enquiry_slot_requests
    add column if not exists event_type varchar(120);

update enquiry_slot_requests slot_request
set event_type = enquiry.event_type
from enquiries enquiry
where slot_request.enquiry_id = enquiry.id
  and slot_request.event_type is null
  and enquiry.event_type is not null;
