-- Publication remains explicit and is not an owner registration or an owner moderation approval.
alter table halls drop constraint ck_halls_listing_ownership;
alter table halls add constraint ck_halls_listing_ownership check (
    (listing_origin='OWNER' and owner_user_id is not null and owner_name is not null and city is not null and area is not null)
    or (listing_origin='APPLICATION' and status in ('DRAFT','APPROVED') and owner_user_id is null and owner_name is null)
);

create table venue_overture_publications (
    hall_id bigint primary key references venue_overture_imports(hall_id),
    publication_version bigint not null default 0 check (publication_version >= 0),
    publication_state varchar(20) not null default 'UNPUBLISHED' check (publication_state in ('UNPUBLISHED','PUBLISHED')),
    published_review_version bigint,
    published_media_version bigint,
    cover_media_id bigint,
    changed_by bigint references admins(id),
    admin_name varchar(180),
    changed_at timestamptz,
    reason varchar(4000),
    check ((publication_version=0 and changed_by is null and admin_name is null and changed_at is null and reason is null)
        or (publication_version>0 and changed_by is not null and admin_name is not null and changed_at is not null and reason is not null and length(reason)>=10)),
    check (publication_state<>'PUBLISHED' or (published_review_version is not null and published_review_version>=0
        and published_media_version is not null and published_media_version>=0 and cover_media_id is not null)),
    foreign key(hall_id,cover_media_id) references venue_overture_draft_photos(hall_id,id)
);
insert into venue_overture_publications(hall_id) select hall_id from venue_overture_imports;

create table venue_overture_publication_photos (
    hall_id bigint not null references venue_overture_publications(hall_id),
    photo_id bigint not null,
    publication_version bigint not null check (publication_version>0),
    sort_order integer not null check (sort_order between 0 and 19),
    primary key(hall_id,photo_id),
    unique(hall_id,sort_order),
    foreign key(hall_id,photo_id) references venue_overture_draft_photos(hall_id,id)
);
create table venue_overture_publication_history (
    hall_id bigint not null references venue_overture_publications(hall_id),
    publication_version bigint not null check (publication_version>0),
    publication_state varchar(20) not null check (publication_state in ('UNPUBLISHED','PUBLISHED')),
    review_version bigint not null check (review_version>=0),
    media_version bigint not null check (media_version>=0),
    cover_media_id bigint,
    photo_ids jsonb not null check (jsonb_typeof(photo_ids)='array' and jsonb_array_length(photo_ids)<=20),
    changed_by bigint not null references admins(id),
    admin_name varchar(180) not null,
    changed_at timestamptz not null default now(),
    reason varchar(4000) not null check (length(reason)>=10),
    primary key(hall_id,publication_version),
    foreign key(hall_id,cover_media_id) references venue_overture_draft_photos(hall_id,id)
);
create function prevent_overture_publication_history_change() returns trigger language plpgsql as $$
begin raise exception 'Application venue publication history is immutable'; end $$;
create trigger trg_overture_publication_history_immutable before update or delete on venue_overture_publication_history
    for each row execute function prevent_overture_publication_history_change();

-- Fail closed even if a generic status-only caller accidentally approves an application venue.
create function check_overture_publication_consistency() returns trigger language plpgsql as $$
declare target bigint; valid boolean;
begin
    if tg_table_name='halls' then target:=new.id;
    elsif tg_op='DELETE' then target:=old.hall_id; else target:=new.hall_id; end if;
    select (h.status='DRAFT' and coalesce(p.publication_state,'UNPUBLISHED')='UNPUBLISHED')
        or (h.status='APPROVED' and p.publication_state='PUBLISHED'
            and p.published_review_version=r.review_version and r.review_status='VERIFIED'
            and r.effective_operating_status='open' and p.published_media_version=m.media_version
            and p.cover_media_id=m.cover_media_id
            and exists(select 1 from venue_overture_publication_history ph where ph.hall_id=h.id
                and ph.publication_version=p.publication_version and ph.publication_state='PUBLISHED'
                and ph.review_version=r.review_version and ph.media_version=m.media_version and ph.cover_media_id=p.cover_media_id)
            and exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp
                on dp.hall_id=pp.hall_id and dp.id=pp.photo_id
                where pp.hall_id=h.id and pp.publication_version=p.publication_version
                    and pp.photo_id=p.cover_media_id and dp.status='APPROVED')
            and not exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp
                on dp.hall_id=pp.hall_id and dp.id=pp.photo_id
                where pp.hall_id=h.id and pp.publication_version=p.publication_version and (dp.status<>'APPROVED' or dp.source_kind='LICENSED_IMAGE'))
            and (select count(*) from venue_overture_publication_photos pp where pp.hall_id=h.id and pp.publication_version=p.publication_version)
                =(select count(*) from venue_overture_draft_photos dp where dp.hall_id=h.id and dp.status='APPROVED')
            and not exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp
                on dp.hall_id=pp.hall_id and dp.id=pp.photo_id where pp.hall_id=h.id and pp.publication_version=p.publication_version
                and pp.sort_order<>(select count(*) from venue_overture_draft_photos previous where previous.hall_id=h.id and previous.status='APPROVED'
                    and (previous.sort_order,previous.id)<(dp.sort_order,dp.id))))
    into valid from halls h left join venue_overture_publications p on p.hall_id=h.id
        left join venue_overture_draft_reviews r on r.hall_id=h.id left join venue_overture_media_state m on m.hall_id=h.id
    where h.id=target and h.listing_origin='APPLICATION';
    if valid is false or (valid is null and exists(select 1 from halls where id=target and listing_origin='APPLICATION')) then
        raise exception 'Application venue requires a consistent explicit publication';
    end if;
    if tg_op='DELETE' then return old; end if;
    return new;
end $$;
create constraint trigger trg_overture_hall_publication_check after insert or update on halls
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
create constraint trigger trg_overture_state_publication_check after insert or update or delete on venue_overture_publications
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
create constraint trigger trg_overture_review_publication_check after update on venue_overture_draft_reviews
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
create constraint trigger trg_overture_media_publication_check after update on venue_overture_media_state
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
create constraint trigger trg_overture_photo_publication_check after update on venue_overture_draft_photos
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
create constraint trigger trg_overture_snapshot_publication_check after insert or update or delete on venue_overture_publication_photos
    deferrable initially deferred for each row execute function check_overture_publication_consistency();

-- The licensed source record is not an editable owner profile.
create function prevent_overture_import_source_change() returns trigger language plpgsql as $$
begin raise exception 'Imported venue source records are immutable'; end $$;
create trigger trg_overture_import_source_immutable before update or delete on venue_overture_imports
    for each row execute function prevent_overture_import_source_change();

create function prevent_live_overture_hall_edit() returns trigger language plpgsql as $$
begin
    if old.listing_origin='APPLICATION' and old.status='APPROVED' and new.status='APPROVED'
        and (to_jsonb(new)-'updated_at') is distinct from (to_jsonb(old)-'updated_at') then
        raise exception 'Unpublish an application venue before editing its live facts';
    end if;
    return new;
end $$;
create trigger trg_overture_live_facts_frozen before update on halls
    for each row execute function prevent_live_overture_hall_edit();

create function prevent_live_overture_workflow_edit() returns trigger language plpgsql as $$
declare target bigint;
begin
    if tg_op='INSERT' then target:=new.hall_id; else target:=old.hall_id; end if;
    if exists(select 1 from venue_overture_publications p join halls h on h.id=p.hall_id
        where p.hall_id=target and p.publication_state='PUBLISHED' and h.status='APPROVED') then
        raise exception 'Unpublish an application venue before editing its live review or photos';
    end if;
    if tg_op='DELETE' then return old; end if;
    return new;
end $$;
create trigger trg_overture_live_review_frozen before insert or update or delete on venue_overture_draft_reviews
    for each row execute function prevent_live_overture_workflow_edit();
create trigger trg_overture_live_media_frozen before insert or update or delete on venue_overture_media_state
    for each row execute function prevent_live_overture_workflow_edit();
create trigger trg_overture_live_photo_frozen before insert or update or delete on venue_overture_draft_photos
    for each row execute function prevent_live_overture_workflow_edit();
