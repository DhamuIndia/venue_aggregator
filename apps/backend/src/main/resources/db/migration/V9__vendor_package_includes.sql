create table if not exists vendor_package_includes (
    package_id bigint not null references vendor_packages(id) on delete cascade,
    include_text varchar(180) not null,
    sort_order integer not null default 0
);
