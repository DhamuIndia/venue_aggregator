-- Preserve legacy review history. Some existing customer/hall pairs can contain
-- an active review plus an older hidden review, so only add the database
-- constraint when the existing rows already satisfy it. The service layer still
-- prevents any new review for a customer/hall pair.
do $$
begin
    if not exists (
        select 1
        from reviews
        group by customer_user_id, hall_id
        having count(*) > 1
    ) and not exists (
        select 1
        from pg_constraint
        where conname = 'uq_review_per_customer_hall'
    ) then
        alter table reviews
            add constraint uq_review_per_customer_hall
            unique (customer_user_id, hall_id);
    end if;
end
$$;
