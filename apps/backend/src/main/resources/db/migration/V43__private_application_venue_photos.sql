-- Draft photos are never rows in public hall_media and never write a public cover URL.
create table venue_overture_media_state (
    hall_id bigint primary key references venue_overture_imports(hall_id),
    media_version bigint not null default 0 check (media_version >= 0),
    cover_media_id bigint
);
insert into venue_overture_media_state(hall_id) select hall_id from venue_overture_imports;

create table venue_overture_draft_photos (
    id bigserial primary key,
    hall_id bigint not null references venue_overture_media_state(hall_id),
    storage_key varchar(180) not null unique,
    size_bytes integer not null check (size_bytes between 1 and 4194304),
    width integer not null check (width between 1 and 6000),
    height integer not null check (height between 1 and 6000),
    sha256 varchar(64) not null check (sha256 ~ '^[a-f0-9]{64}$'),
    caption varchar(500),
    source_kind varchar(30) not null check (source_kind in ('TEAM_PHOTO','BUSINESS_PROVIDED','LICENSED_IMAGE')),
    source_reference varchar(2048),
    rights_basis varchar(30) not null,
    license_name varchar(180),
    permission_evidence varchar(4000) not null check (length(permission_evidence) >= 10),
    rights_confirmed boolean not null check (rights_confirmed),
    uploaded_by bigint not null references admins(id),
    uploader_name varchar(180) not null,
    uploaded_at timestamptz not null default now(),
    status varchar(20) not null default 'PENDING' check (status in ('PENDING','APPROVED','REJECTED','ARCHIVED')),
    sort_order integer not null check (sort_order between 0 and 100),
    reviewed_by bigint references admins(id),
    reviewer_name varchar(180),
    reviewed_at timestamptz,
    review_reason varchar(4000),
    archived_by bigint references admins(id),
    archiver_name varchar(180),
    archived_at timestamptz,
    archive_reason varchar(4000),
    unique(hall_id,id),
    check (width::bigint * height <= 16000000),
    check (storage_key ~ '^drafts/[1-9][0-9]*/[a-f0-9-]{36}\.jpg$'),
    check ((source_kind='TEAM_PHOTO' and rights_basis='TEAM_OWNED' and license_name is null)
        or (source_kind='BUSINESS_PROVIDED' and rights_basis='BUSINESS_PERMISSION' and license_name is null)
        or (source_kind='LICENSED_IMAGE' and rights_basis='OPEN_LICENSE' and source_reference is not null and license_name is not null)),
    check ((reviewed_by is null and reviewer_name is null and reviewed_at is null and review_reason is null)
        or (reviewed_by is not null and reviewer_name is not null and reviewed_at is not null and review_reason is not null and length(review_reason) >= 10)),
    check ((status='ARCHIVED' and archived_by is not null and archiver_name is not null and archived_at is not null and archive_reason is not null and length(archive_reason) >= 10)
        or (status<>'ARCHIVED' and archived_by is null and archiver_name is null and archived_at is null and archive_reason is null)),
    check (status not in ('APPROVED','REJECTED') or reviewed_by is not null),
    check (status<>'PENDING' or reviewed_by is null)
);
alter table venue_overture_media_state add constraint fk_overture_private_cover
    foreign key(hall_id,cover_media_id) references venue_overture_draft_photos(hall_id,id);
create index ix_overture_private_photos_hall_order on venue_overture_draft_photos(hall_id,sort_order,id);

create function prevent_overture_photo_provenance_update() returns trigger language plpgsql as $$
begin
    if row(new.hall_id,new.storage_key,new.size_bytes,new.width,new.height,new.sha256,new.caption,new.source_kind,
           new.source_reference,new.rights_basis,new.license_name,new.permission_evidence,new.rights_confirmed,
           new.uploaded_by,new.uploader_name,new.uploaded_at)
       is distinct from
       row(old.hall_id,old.storage_key,old.size_bytes,old.width,old.height,old.sha256,old.caption,old.source_kind,
           old.source_reference,old.rights_basis,old.license_name,old.permission_evidence,old.rights_confirmed,
           old.uploaded_by,old.uploader_name,old.uploaded_at) then
        raise exception 'Private photo content and provenance are immutable';
    end if;
    if old.reviewed_by is not null and row(new.reviewed_by,new.reviewer_name,new.reviewed_at,new.review_reason)
       is distinct from row(old.reviewed_by,old.reviewer_name,old.reviewed_at,old.review_reason) then
        raise exception 'Private photo review history is immutable';
    end if;
    if old.status='ARCHIVED' then raise exception 'Archived private photos are immutable'; end if;
    if old.status<>'PENDING' and new.status not in (old.status,'ARCHIVED') then
        raise exception 'Reviewed private photos cannot be reviewed again';
    end if;
    return new;
end $$;
create trigger trg_overture_photo_provenance_immutable before update on venue_overture_draft_photos
    for each row execute function prevent_overture_photo_provenance_update();
