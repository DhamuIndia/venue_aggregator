alter table vendors
    add column if not exists reviewed_by_admin_id bigint references admins(id),
    add column if not exists reviewed_at timestamptz;
