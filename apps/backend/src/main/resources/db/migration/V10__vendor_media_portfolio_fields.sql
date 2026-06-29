alter table vendor_media
    add column if not exists media_type varchar(20) not null default 'IMAGE',
    add column if not exists storage_key varchar(500),
    add column if not exists file_name varchar(255),
    add column if not exists caption varchar(180),
    add column if not exists sort_order integer not null default 0;

create index if not exists ix_vendor_media_vendor_id_sort
    on vendor_media(vendor_id, sort_order);
