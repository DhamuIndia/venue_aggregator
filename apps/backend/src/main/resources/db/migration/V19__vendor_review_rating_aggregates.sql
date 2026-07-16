alter table vendors
    add column if not exists average_rating double precision not null default 0,
    add column if not exists review_count integer not null default 0;

update vendors v
set average_rating = coalesce(aggregates.average_rating, 0),
    review_count = coalesce(aggregates.review_count, 0)
from (
    select vendor_id,
           round(avg(rating)::numeric, 1)::double precision as average_rating,
           count(*)::integer as review_count
    from reviews
    where vendor_id is not null
      and active = true
      and moderation_status = 'PUBLISHED'
    group by vendor_id
) aggregates
where v.id = aggregates.vendor_id;
