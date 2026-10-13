-- Private factual review is separate from ownership, moderation and publication.
create table venue_overture_draft_reviews (
    hall_id bigint primary key references venue_overture_imports(hall_id),
    source_facts jsonb not null,
    review_version bigint not null default 0 check (review_version >= 0),
    review_status varchar(30) not null default 'UNREVIEWED'
        check (review_status in ('UNREVIEWED','IN_REVIEW','VERIFIED','DUPLICATE')),
    verifications jsonb not null default '{}'::jsonb,
    effective_website varchar(2048),
    effective_operating_status varchar(40) not null default 'unknown'
        check (effective_operating_status in ('unknown','open','temporarily_closed','permanently_closed')),
    duplicate_decision varchar(30) not null default 'NOT_REVIEWED'
        check (duplicate_decision in ('NOT_REVIEWED','DISTINCT','CONFIRMED_DUPLICATE')),
    duplicate_notes varchar(4000),
    reviewed_duplicate_hall_ids jsonb not null default '[]'::jsonb,
    review_notes varchar(4000),
    last_reviewed_by bigint references admins(id),
    last_reviewer_name varchar(180),
    last_reviewed_at timestamptz,
    check (jsonb_typeof(source_facts) = 'object' and jsonb_typeof(verifications) = 'object'
        and jsonb_typeof(reviewed_duplicate_hall_ids) = 'array'),
    check ((last_reviewed_by is null and last_reviewed_at is null and last_reviewer_name is null)
        or (last_reviewed_by is not null and last_reviewed_at is not null and last_reviewer_name is not null))
);

-- Phase 3 had no editor, so existing imported halls still contain their source facts.
insert into venue_overture_draft_reviews (hall_id, source_facts, effective_website, effective_operating_status)
select h.id, jsonb_build_object(
    'name',h.name,'address',h.address_line,'city',h.city,'area',h.area,'postcode',h.pincode,
    'latitude',h.latitude,'longitude',h.longitude,'phone',h.contact_number,'website',i.source_website,
    'operatingStatus',coalesce(i.source_operating_status,'unknown'),'capacity',h.capacity_max,'description',h.description,
    'amenities',jsonb_build_object('ac',h.ac_available,'carParking',h.car_parking,'bikeParking',h.bike_parking,
        'dining',h.dining_available,'generator',h.generator_available,'lift',h.lift_available,
        'bridalRoom',h.bridal_room_available,'cateringKitchen',h.catering_kitchen_available)),
    i.source_website, coalesce(i.source_operating_status,'unknown')
from venue_overture_imports i join halls h on h.id=i.hall_id;

-- Preserve the imported snapshot even if a future caller accidentally tries to edit it.
create function prevent_overture_source_facts_update() returns trigger language plpgsql as $$
begin
    if new.source_facts is distinct from old.source_facts then
        raise exception 'Overture source facts are immutable';
    end if;
    return new;
end $$;
create trigger trg_overture_source_facts_immutable before update on venue_overture_draft_reviews
    for each row execute function prevent_overture_source_facts_update();

create index ix_halls_draft_review_name_city on halls
    (lower(btrim(regexp_replace(normalize(name,NFKC), '[[:space:]]+', ' ', 'g'))),
     lower(btrim(regexp_replace(normalize(city,NFKC), '[[:space:]]+', ' ', 'g'))));
create index ix_halls_draft_review_coordinates on halls(latitude,longitude);
