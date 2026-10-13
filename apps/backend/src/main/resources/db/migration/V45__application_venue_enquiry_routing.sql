-- Existing enquiries keep their owner/vendor workflow. Application-managed venues
-- have a separate team route and can never create a booking through enquiry status.
alter table enquiries
    add column routing_target varchar(20) not null default 'OWNER',
    add column publication_version bigint,
    add column team_response_message text,
    add column last_team_update_reason text,
    add column team_updated_by bigint references admins(id);

alter table enquiries add constraint ck_enquiries_routing_target check (
    (routing_target = 'OWNER' and publication_version is null
        and team_response_message is null and last_team_update_reason is null and team_updated_by is null)
    or (routing_target = 'VENUEMART' and hall_id is not null and vendor_id is null
        and customer_user_id is not null and publication_version is not null and publication_version > 0
        and version is not null and version >= 0
        and owner_response_message is null and status in ('NEW', 'CONTACTED', 'CLOSED')
        and ((status = 'NEW' and team_response_message is null and last_team_update_reason is null
                and team_updated_by is null and responded_at is null)
            or (status in ('CONTACTED', 'CLOSED') and team_response_message is not null
                and char_length(btrim(team_response_message)) between 1 and 2000
                and last_team_update_reason is not null
                and char_length(btrim(last_team_update_reason)) between 10 and 1000
                and team_updated_by is not null and responded_at is not null)))
);

create index ix_enquiries_team_queue on enquiries(status, created_at desc, id desc)
    where routing_target = 'VENUEMART';

create function enforce_application_enquiry_routing() returns trigger language plpgsql as $$
declare origin varchar(20);
begin
    if TG_OP = 'UPDATE' then
        if new.routing_target is distinct from old.routing_target
            or new.publication_version is distinct from old.publication_version then
            raise exception 'Enquiry routing and publication snapshot are immutable';
        end if;
        if old.routing_target = 'VENUEMART' then
            if new.hall_id is distinct from old.hall_id or new.customer_user_id is distinct from old.customer_user_id
                or new.vendor_id is distinct from old.vendor_id then
                raise exception 'VenueMart enquiry participants are immutable';
            end if;
            if not ((old.status = 'NEW' and new.status = 'CONTACTED')
                or (old.status = 'CONTACTED' and new.status = 'CLOSED')) then
                raise exception 'VenueMart enquiries must progress from new to contacted to closed';
            end if;
            if new.version is distinct from old.version + 1 then
                raise exception 'VenueMart enquiry update must increment its version';
            end if;
        end if;
    end if;
    if new.hall_id is not null then
        select listing_origin into origin from halls where id = new.hall_id;
        if (new.routing_target = 'VENUEMART' and origin is distinct from 'APPLICATION')
            or (new.routing_target = 'OWNER' and origin = 'APPLICATION') then
            raise exception 'Enquiry route must match the venue management origin';
        end if;
    end if;
    if TG_OP = 'INSERT' and new.routing_target = 'VENUEMART' and new.status <> 'NEW' then
        raise exception 'VenueMart enquiries start as new';
    end if;
    return new;
end;
$$;

create trigger trg_application_enquiry_routing before insert or update on enquiries
    for each row execute function enforce_application_enquiry_routing();

create function prevent_application_venue_bookings() returns trigger language plpgsql as $$
begin
    if exists (select 1 from halls where id = new.hall_id and listing_origin = 'APPLICATION')
        or exists (select 1 from enquiries where id = new.enquiry_id and routing_target = 'VENUEMART') then
        raise exception 'Application-managed venue requests cannot create bookings';
    end if;
    return new;
end;
$$;

create trigger trg_prevent_application_venue_bookings before insert or update on bookings
    for each row execute function prevent_application_venue_bookings();
