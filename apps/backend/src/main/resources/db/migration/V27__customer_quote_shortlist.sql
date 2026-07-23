alter table vendor_quotes
    add column shortlisted boolean not null default false;

alter table vendor_quotes
    add column shortlisted_at timestamptz;

alter table vendor_quotes
    add constraint ck_vendor_quotes_shortlist_timestamp
        check (shortlisted = (shortlisted_at is not null));

create index idx_vendor_quotes_shortlisted
    on vendor_quotes(shortlisted)
    where shortlisted = true;
