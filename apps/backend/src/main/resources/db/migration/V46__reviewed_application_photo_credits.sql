-- Private rights evidence is never a public credit. Revisions and publication credits are append-only.
create function overture_safe_credit_text(value text, max_length integer, multiline boolean default false)
returns boolean language sql immutable as $$
    select coalesce(value is not null and length(value) between 1 and max_length and value !~ '^[[:space:]]*$'
        and (case when multiline then translate(value,E'\n\r\t','') else value end) !~ '[[:cntrl:]]'
        and value !~ U&'[\007F-\009F\00AD\0600-\0605\061C\06DD\070F\0890-\0891\08E2\180E\200B-\200F\202A-\202E\2060-\206F\FEFF\FFF9-\FFFB\+0110BD\+0110CD\+013430-\+013455\+01BCA0-\+01BCA3\+01D173-\+01D17A\+0E0001\+0E0020-\+0E007F]',false)
$$;

-- These are display links, never URLs fetched by the application. No credentials, secret query, or IP/local hosts.
create function overture_safe_public_credit_url(value text) returns boolean language plpgsql immutable as $$
declare host text; label text; decoded text; next_value text; part text; hex_run text; i integer; pass integer;
begin
    if not overture_safe_credit_text(value,2048) or value ~ '[[:space:]]'
        or value !~ '^https://[A-Za-z0-9.-]+(:443)?(/[^?#@\\]*)?$' then return false; end if;
    host:=lower(substring(value from '^https://([^/:]+)'));
    if host is null or length(host)>253 or position('.' in host)=0 or host !~ '[a-z]'
        or host ~ '^(0x[0-9a-f]+|[0-9]+)(\.(0x[0-9a-f]+|[0-9]+))+$'
        or host ~ '(^|\.)(localhost|local|internal|home|lan|corp|invalid|test|example|onion|arpa)$' then return false; end if;
    foreach label in array string_to_array(host,'.') loop
        if length(label) not between 1 and 63 or label !~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$' then return false; end if;
    end loop;
    decoded:=value;
    for pass in 1..16 loop
        next_value:=''; i:=1;
        while i<=length(decoded) loop
            part:=substr(decoded,i,1);
            if part='%' then
                if substr(decoded,i+1,2) !~* '^[a-f0-9]{2}$' then
                    if pass=1 then return false; end if;
                    -- A valid first layer may decode an ordinary literal percent in a filename.
                    next_value:=next_value||part; i:=i+1; continue;
                end if;
                hex_run:='';
                while substr(decoded,i,1)='%' and substr(decoded,i+1,2) ~* '^[a-f0-9]{2}$' loop
                    hex_run:=hex_run||substr(decoded,i+1,2); i:=i+3;
                end loop;
                -- PostgreSQL rejects malformed UTF-8 and NUL instead of accepting replacement characters.
                next_value:=next_value||convert_from(decode(hex_run,'hex'),'UTF8');
            else next_value:=next_value||part; i:=i+1; end if;
        end loop;
        if not overture_safe_credit_text(next_value,2048) or position(E'\\' in next_value)>0 or position('@' in next_value)>0 then return false; end if;
        if decoded=next_value then exit; end if;
        decoded:=next_value;
    end loop;
    if decoded ~* '%[a-f0-9]{2}' then return false; end if;
    decoded:=lower(decoded);
    if host ~ 'googleusercontent|ggpht|gstatic' or host ~ '(^|\.)(photos|maps)\.google\.'
        or host in ('maps.app.goo.gl','goo.gl')
        or (host ~ '(^|\.)google\.' and decoded ~ '/(maps|photos)([/?#]|$)') then return false; end if;
    return true;
exception when others then return false;
end $$;

create function overture_photo_license_code(value text) returns text language sql immutable as $$
    select case upper(btrim(regexp_replace(value,'[[:space:]]+',' ','g')))
        when 'CC BY 4.0' then 'CC_BY_4_0' when 'CC-BY-4.0' then 'CC_BY_4_0'
        when 'CREATIVE COMMONS ATTRIBUTION 4.0 INTERNATIONAL' then 'CC_BY_4_0'
        when 'CC0 1.0' then 'CC0_1_0' when 'CC0-1.0' then 'CC0_1_0'
        when 'CC0 1.0 UNIVERSAL' then 'CC0_1_0' when 'CREATIVE COMMONS ZERO 1.0 UNIVERSAL' then 'CC0_1_0'
        else null end
$$;

create table venue_overture_photo_credits (
    hall_id bigint not null,
    photo_id bigint not null,
    credit_version bigint not null check (credit_version between 1 and 100),
    title varchar(200) not null check (overture_safe_credit_text(title,200)),
    creator varchar(300) not null check (overture_safe_credit_text(creator,300)),
    creator_url varchar(2048) check (creator_url is null or overture_safe_public_credit_url(creator_url)),
    source_url varchar(2048) not null check (overture_safe_public_credit_url(source_url)),
    license_code varchar(20) not null check (license_code in ('CC_BY_4_0','CC0_1_0')),
    changes_notice varchar(1000) not null check (overture_safe_credit_text(changes_notice,1000,true)),
    required_notices varchar(2000) check (required_notices is null or overture_safe_credit_text(required_notices,2000,true)),
    review_status varchar(20) not null check (review_status in ('PENDING','APPROVED','REJECTED')),
    changed_by bigint not null references admins(id),
    admin_name varchar(180) not null check (overture_safe_credit_text(admin_name,180)),
    changed_at timestamptz not null default now(),
    reviewed_by bigint references admins(id),
    reviewer_name varchar(180),
    reviewed_at timestamptz,
    review_reason varchar(4000),
    rights_confirmed boolean not null,
    attribution_confirmed boolean not null,
    primary key(hall_id,photo_id,credit_version),
    foreign key(hall_id,photo_id) references venue_overture_draft_photos(hall_id,id),
    check ((review_status='PENDING' and reviewed_by is null and reviewer_name is null and reviewed_at is null and review_reason is null
            and not rights_confirmed and not attribution_confirmed)
        or (review_status in ('APPROVED','REJECTED') and reviewed_by is not null and reviewer_name is not null and reviewed_at is not null
            and review_reason is not null and length(review_reason)>=10 and overture_safe_credit_text(reviewer_name,180)
            and overture_safe_credit_text(review_reason,4000,true))),
    check (review_status<>'APPROVED' or (rights_confirmed and attribution_confirmed))
);

create function prevent_overture_photo_credit_change() returns trigger language plpgsql as $$
begin raise exception 'Application photo credit revisions are immutable'; end $$;
create trigger trg_overture_photo_credit_immutable before update or delete on venue_overture_photo_credits
    for each row execute function prevent_overture_photo_credit_change();

create function check_overture_photo_credit_insert() returns trigger language plpgsql as $$
declare previous bigint; photo record;
begin
    -- Match the application lock order. SQL-side writers cannot append while the venue is live.
    perform 1 from halls where id=new.hall_id and listing_origin='APPLICATION' and status='DRAFT'
        and owner_user_id is null and owner_name is null for update;
    if not found then raise exception 'Unpublish an application venue before editing its photo credits'; end if;
    if exists(select 1 from venue_overture_publications where hall_id=new.hall_id and publication_state='PUBLISHED') then
        raise exception 'Unpublish an application venue before editing its photo credits';
    end if;
    select * into photo from venue_overture_draft_photos where hall_id=new.hall_id and id=new.photo_id;
    if not found or photo.source_kind<>'LICENSED_IMAGE' or photo.status='ARCHIVED' then
        raise exception 'Public credits apply only to retained licensed draft photos';
    end if;
    select coalesce(max(credit_version),0) into previous from venue_overture_photo_credits where hall_id=new.hall_id and photo_id=new.photo_id;
    if new.credit_version<>previous+1 then raise exception 'Photo credit revisions must be sequential'; end if;
    if new.review_status='APPROVED' and (photo.status<>'APPROVED'
        or overture_photo_license_code(photo.license_name) is distinct from new.license_code) then
        raise exception 'Approved photo credits require an approved photo and matching supported original license';
    end if;
    return new;
end $$;
create trigger trg_overture_photo_credit_insert before insert on venue_overture_photo_credits
    for each row execute function check_overture_photo_credit_insert();

create function overture_photo_credit_ready(target_hall bigint,target_photo bigint,target_credit bigint)
returns boolean language sql stable as $$
    select exists(select 1 from venue_overture_photo_credits c join venue_overture_draft_photos dp on dp.hall_id=c.hall_id and dp.id=c.photo_id
        where c.hall_id=target_hall and c.photo_id=target_photo and c.credit_version=target_credit
            and c.credit_version=(select max(latest.credit_version) from venue_overture_photo_credits latest where latest.hall_id=c.hall_id and latest.photo_id=c.photo_id)
            and c.review_status='APPROVED' and c.rights_confirmed and c.attribution_confirmed and dp.status='APPROVED'
            and dp.source_kind='LICENSED_IMAGE' and overture_photo_license_code(dp.license_name)=c.license_code)
$$;

-- This whitelist is the entire public subset, including null optional fields. No actor/reason/evidence keys.
create function overture_public_photo_credit_snapshot(target_hall bigint,target_photo bigint,target_credit bigint)
returns jsonb language sql stable as $$
    select jsonb_build_object('title',c.title,'creator',c.creator,'creatorUrl',c.creator_url,'sourceUrl',c.source_url,
        'licenseCode',c.license_code,'licenseLabel',case c.license_code when 'CC_BY_4_0' then 'CC BY 4.0' when 'CC0_1_0' then 'CC0 1.0' end,
        'licenseUrl',case c.license_code when 'CC_BY_4_0' then 'https://creativecommons.org/licenses/by/4.0/'
            when 'CC0_1_0' then 'https://creativecommons.org/publicdomain/zero/1.0/' end,
        'changesNotice',c.changes_notice,'processingNotice','VenueMart normalized this image to JPEG and may have resized it.',
        'requiredNotices',c.required_notices)
    from venue_overture_photo_credits c where c.hall_id=target_hall and c.photo_id=target_photo and c.credit_version=target_credit and c.review_status='APPROVED'
$$;

create table venue_overture_publication_photo_credits (
    hall_id bigint not null,
    publication_version bigint not null check (publication_version>0),
    photo_id bigint not null,
    credit_version bigint not null,
    credit_snapshot jsonb not null check (jsonb_typeof(credit_snapshot)='object'),
    primary key(hall_id,publication_version,photo_id),
    foreign key(hall_id,photo_id,credit_version) references venue_overture_photo_credits(hall_id,photo_id,credit_version),
    foreign key(hall_id,publication_version) references venue_overture_publication_history(hall_id,publication_version) deferrable initially deferred
);
create trigger trg_overture_public_photo_credit_immutable before update or delete on venue_overture_publication_photo_credits
    for each row execute function prevent_overture_photo_credit_change();

create function overture_publication_photo_credit_valid(target_hall bigint,target_photo bigint,target_publication bigint)
returns boolean language sql stable as $$
    select exists(select 1 from venue_overture_publication_photo_credits pc
        where pc.hall_id=target_hall and pc.photo_id=target_photo and pc.publication_version=target_publication
            and overture_photo_credit_ready(pc.hall_id,pc.photo_id,pc.credit_version)
            and pc.credit_snapshot=overture_public_photo_credit_snapshot(pc.hall_id,pc.photo_id,pc.credit_version))
$$;

create function check_overture_public_photo_credit_insert() returns trigger language plpgsql as $$
begin
    if not exists(select 1 from venue_overture_publications p
        join venue_overture_publication_photos pp on pp.hall_id=p.hall_id and pp.publication_version=p.publication_version
        where p.hall_id=new.hall_id and p.publication_version=new.publication_version and p.publication_state='PUBLISHED' and pp.photo_id=new.photo_id)
        or not overture_photo_credit_ready(new.hall_id,new.photo_id,new.credit_version)
        or new.credit_snapshot is distinct from overture_public_photo_credit_snapshot(new.hall_id,new.photo_id,new.credit_version) then
        raise exception 'Publication photo credits require the current reviewed safe credit snapshot';
    end if;
    return new;
end $$;
create trigger trg_overture_public_photo_credit_insert before insert on venue_overture_publication_photo_credits
    for each row execute function check_overture_public_photo_credit_insert();

-- Retain every 4C invariant, replacing only its unconditional licensed-photo ban with structural credit proof.
create or replace function check_overture_publication_consistency() returns trigger language plpgsql as $$
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
                where pp.hall_id=h.id and pp.publication_version=p.publication_version and pp.photo_id=p.cover_media_id and dp.status='APPROVED')
            and not exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp
                on dp.hall_id=pp.hall_id and dp.id=pp.photo_id
                where pp.hall_id=h.id and pp.publication_version=p.publication_version and (dp.status<>'APPROVED'
                    or (dp.source_kind='LICENSED_IMAGE' and not overture_publication_photo_credit_valid(h.id,dp.id,p.publication_version))))
            and not exists(select 1 from venue_overture_publication_photo_credits pc
                left join venue_overture_publication_photos pp on pp.hall_id=pc.hall_id and pp.photo_id=pc.photo_id and pp.publication_version=pc.publication_version
                left join venue_overture_draft_photos dp on dp.hall_id=pc.hall_id and dp.id=pc.photo_id
                where pc.hall_id=h.id and pc.publication_version=p.publication_version and (pp.photo_id is null or dp.source_kind<>'LICENSED_IMAGE'))
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
create constraint trigger trg_overture_credit_publication_check after insert on venue_overture_photo_credits
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
create constraint trigger trg_overture_public_credit_publication_check after insert on venue_overture_publication_photo_credits
    deferrable initially deferred for each row execute function check_overture_publication_consistency();
