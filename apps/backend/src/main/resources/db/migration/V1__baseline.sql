create table roles (
    id bigserial primary key,
    name varchar(50) not null unique,
    created_at timestamptz not null default now()
);

create table users (
    id bigserial primary key,
    email varchar(255) not null unique,
    -- password_hash varchar(255) not null,
    full_name varchar(160) not null,
    phone varchar(32),
    status varchar(40) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table user_roles (
    user_id bigint not null references users(id) on delete cascade,
    role_id bigint not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);

-- create table halls (
--     id bigserial primary key,
--     owner_user_id bigint not null references users(id),
--     owner_name varchar(100) not null,
--     name varchar(180) not null,
--     description text,
--     cover_image_url varchar(255) not null,
--     address_line varchar(120) not null,
--     city varchar(120) not null,
--     area varchar(120) not null,
--     pincode varchar(16),
--     latitude double precision,
--     longitude double precision,
--     capacity_min integer,
--     capacity_max integer,
--     floors integer,
--     ac_available boolean,
--     hall_type varchar(50),
--     ratings float,
--     rooms integer,
--     car_parking boolean,
--     bike_parking boolean,
--     dining_available boolean,
--     amount numeric(12, 2) not null,
--     contact_number varchar(20),
--     whatsapp_number varchar(20),
--     dining_capacity integer,
--     generator_available boolean,
--     lift_available boolean,
--     status varchar(40) not null default 'PENDING_APPROVAL',
--     rejection_reason varchar(120),
--     created_at timestamptz not null default now(),
--     updated_at timestamptz not null default now()
-- );

-- create table hall_media (
--     id bigserial primary key,
--     hall_id bigint not null references halls(id) on delete cascade,
--     media_type varchar(20) not null,
--     url text not null,
--     public_id varchar(255),
--     is_primary boolean not null default false,
--     sort_order integer not null default 0,
--     created_at timestamptz not null default now()
-- );

-- create table hall_blocked_dates (
--     id bigserial primary key,
--     hall_id bigint not null references halls(id) on delete cascade,
--     event_date date not null,
--     slot_type varchar(30) not null,
--     reason varchar(255),
--     created_at timestamptz not null default now(),
--     unique (hall_id, event_date, slot_type)
-- );

create table bookings (
    id bigserial primary key,
    -- hall_id bigint not null references halls(id),
    customer_user_id bigint references users(id),
    event_date date not null,
    slot_type varchar(30) not null,
    status varchar(40) not null default 'ENQUIRY',
    customer_name varchar(160) not null,
    customer_phone varchar(32) not null,
    customer_email varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- create unique index uq_bookings_confirmed_hall_date_slot
--     on bookings(hall_id, event_date, slot_type)
--     where status = 'CONFIRMED';

create table vendor_categories (
    id bigserial primary key,
    name varchar(120) not null unique,
    created_at timestamptz not null default now()
);

create table vendors (
    id bigserial primary key,
    -- user_id bigint not null references users(id),
    vendor_name varchar(100) not null,
    cover_image_url varchar(255) not null,
    business_name varchar(180) not null,
    description text,
    address_line varchar(120) not null,
    city varchar(120) not null,
    area varchar(120),
    pincode varchar(120),
    latitude double precision,
    longitude double precision,
    email varchar(50) unique,
    contact_number varchar(20),
    whatsapp_number varchar(20),
    password_hash varchar(100) not null,
    status varchar(40) not null default 'PENDING',
    rejection_reason varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table vendor_dj_details (
    id bigserial primary key,
    vendor_id bigint not null unique references vendors(id),
    experience_years integer,
    sound_system_available boolean,
    travel_distance_km integer,
    starting_price numeric(12,2),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    status varchar(15) not null default 'PENDING',
    rejection_reason varchar(255) 
);

CREATE TABLE vendor_photography_details (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    experience_years INTEGER,
    candid_photography BOOLEAN,
    videography_available BOOLEAN,
    drone_available BOOLEAN,
    album_included BOOLEAN,
    starting_price NUMERIC(12,2),
    status varchar(15) not null default 'PENDING',
    rejection_reason varchar(255)
);

CREATE TABLE vendor_makeup_details (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    experience_years INTEGER,
    bridal_makeup BOOLEAN,
    home_service BOOLEAN,
    products_used VARCHAR(255),
    starting_price NUMERIC(12,2),
    status varchar(15) not null default 'PENDING',
    rejection_reason varchar(255)
);

CREATE TABLE vendor_catering_details (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    veg_available BOOLEAN,
    non_veg_available BOOLEAN,
    service_type VARCHAR(30),
    min_guest_count INTEGER,
    max_guest_count INTEGER,
    live_counter_available BOOLEAN,
    starting_price_per_plate NUMERIC(12,2),
    status varchar(15) not null default 'PENDING',
    rejection_reason varchar(255)
);

CREATE TABLE vendor_decoration_details (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    experience_years INTEGER,
    flower_decoration_available BOOLEAN,
    balloon_decoration_available BOOLEAN,
    stage_decoration_available BOOLEAN,
    theme_decoration_available BOOLEAN,
    starting_price NUMERIC(12,2),
    status varchar(15) not null default 'PENDING',
    rejection_reason varchar(255)
);

CREATE TABLE vendor_hall_details (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    capacity_min INTEGER,
    capacity_max INTEGER,
    floors INTEGER,
    rooms INTEGER,
    hall_type VARCHAR(50),
    ac_available BOOLEAN,
    lift_available BOOLEAN,
    generator_available BOOLEAN,
    car_parking BOOLEAN,
    bike_parking BOOLEAN,
    dining_available BOOLEAN,
    dining_capacity INTEGER,
    amount NUMERIC(12,2),
    status varchar(15) not null default 'PENDING',
    rejection_reason varchar(255)
);

create table vendor_category_mapping (
    vendor_id bigint not null,
    category_id bigint not null,
    primary key (vendor_id, category_id),
    foreign key (vendor_id)
        references vendors(id),
    foreign key (category_id)
        references vendor_categories(id)
);

create table subscription_plans (
    id bigserial primary key,
    name varchar(120) not null unique,
    price_amount numeric(12, 2) not null,
    currency varchar(8) not null default 'INR',
    billing_period varchar(30) not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table vendor_subscriptions (
    id bigserial primary key,
    vendor_id bigint not null references vendors(id),
    plan_id bigint not null references subscription_plans(id),
    status varchar(40) not null default 'PENDING',
    razorpay_subscription_id varchar(255),
    starts_at timestamptz,
    ends_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table enquiries (
    id bigserial primary key,
    -- hall_id bigint references halls(id),
    vendor_id bigint references vendors(id),
    customer_name varchar(160) not null,
    customer_phone varchar(32) not null,
    customer_email varchar(255),
    event_date date,
    message text,
    status varchar(40) not null default 'NEW',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
    -- constraint enquiries_target_check check (hall_id is not null or vendor_id is not null)
);

create table payments (
    id bigserial primary key,
    vendor_subscription_id bigint references vendor_subscriptions(id),
    amount numeric(12, 2) not null,
    currency varchar(8) not null default 'INR',
    status varchar(40) not null,
    razorpay_payment_id varchar(255),
    razorpay_order_id varchar(255),
    created_at timestamptz not null default now()
);

create table admins (
    id bigserial primary key,
    full_name varchar(160) not null,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    contact_number varchar(20) not null,
    status varchar(40) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table vendor_media (
    id bigserial primary key,
    vendor_id bigint not null references vendors(id),
    service_type varchar(15) not null,
    service_id bigint not null,
    media_url text not null,
    is_primary boolean default false,
    created_at timestamptz default now()
);

create table vendor_packages (
    id bigserial primary key,
    vendor_id bigint not null references vendors(id),
    service_type varchar(15) not null,
    service_id bigint not null,
    package_name varchar(150) not null,
    description text,
    price numeric(12,2),
    created_at timestamptz default now()
);

create table vendor_blocked_dates (
    id bigserial primary key,
    vendor_id bigint not null references vendors(id),
    service_type varchar(15) not null,
    service_id bigint not null,
    event_date date not null,
    slot_type varchar(30) not null,
    reason varchar(255),
    created_at timestamptz default now()
);

insert into roles(name) values
    ('CUSTOMER'),
    ('HALL_OWNER'),
    ('VENDOR'),
    ('ADMIN'),
    ('SUPER_ADMIN');

insert into vendor_categories(name) values
    ('Photography'),
    ('DJ'),
    ('Food'),
    ('Hall'),
    ('Decoration'),
    ('Makeup'),
    ('Mehendi'),
    ('Catering'),
    ('Balloon Decoration'),
    ('Live Music'),
    ('Wedding Planner');
