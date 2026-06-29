alter table halls
    add column if not exists cover_image_url varchar(255),
    add column if not exists address_line varchar(120),
    add column if not exists floors integer,
    add column if not exists rooms integer,
    add column if not exists dining_capacity integer,
    add column if not exists generator_available boolean,
    add column if not exists lift_available boolean,
    add column if not exists bridal_room_available boolean,
    add column if not exists catering_kitchen_available boolean,
    add column if not exists morning_amount numeric(12, 2),
    add column if not exists evening_amount numeric(12, 2),
    add column if not exists full_day_amount numeric(12, 2),
    add column if not exists approved_by bigint references admins(id),
    add column if not exists approved_at timestamptz;

alter table halls
    alter column status set default 'DRAFT';

create index if not exists ix_halls_status
    on halls(status);

create index if not exists ix_halls_owner_user_id
    on halls(owner_user_id);

create index if not exists ix_halls_city_area
    on halls(city, area);

create index if not exists ix_hall_media_hall_id_sort
    on hall_media(hall_id, sort_order);
