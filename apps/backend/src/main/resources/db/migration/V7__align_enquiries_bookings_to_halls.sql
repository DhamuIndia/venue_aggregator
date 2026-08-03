alter table enquiries
    drop constraint if exists enquiries_hall_id_fkey;

alter table bookings
    drop constraint if exists bookings_hall_id_fkey;

update enquiries
set hall_id = null
where hall_id is not null
  and not exists (
      select 1
      from halls
      where halls.id = enquiries.hall_id
  );

update bookings
set hall_id = null
where hall_id is not null
  and not exists (
      select 1
      from halls
      where halls.id = bookings.hall_id
  );

alter table enquiries
    add constraint enquiries_hall_id_fkey
    foreign key (hall_id) references halls(id);

alter table bookings
    add constraint bookings_hall_id_fkey
    foreign key (hall_id) references halls(id);

update bookings
set amount = coalesce(bookings.amount, halls.full_day_amount, halls.evening_amount, halls.morning_amount)
from halls
where bookings.hall_id = halls.id
  and bookings.amount is null;
