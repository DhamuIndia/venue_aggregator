alter table hall_media
    add column if not exists approved boolean not null default false;

alter table vendor_media
    add column if not exists approved boolean not null default false;

-- Existing media was already live before this policy; preserve its current visibility.
update hall_media set approved = true where approved = false;
update vendor_media set approved = true where approved = false;
