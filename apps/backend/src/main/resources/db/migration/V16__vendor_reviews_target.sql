alter table reviews
    add column if not exists vendor_id bigint;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_reviews_vendor_id'
    ) then
        alter table reviews
            add constraint fk_reviews_vendor_id
            foreign key (vendor_id) references vendors(id);
    end if;
end $$;

alter table reviews
    alter column booking_id drop not null,
    alter column enquiry_id drop not null,
    alter column hall_id drop not null;

create index if not exists idx_reviews_vendor_published
    on reviews(vendor_id, created_at desc)
    where active = true and moderation_status = 'PUBLISHED';
