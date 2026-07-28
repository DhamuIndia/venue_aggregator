alter table halls add column if not exists pending_update_payload text;
alter table vendors add column if not exists pending_update_payload text;
