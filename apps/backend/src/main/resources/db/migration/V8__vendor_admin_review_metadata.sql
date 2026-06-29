alter table vendors
    add column if not exists reviewed_by_user_id bigint references users(id),
    add column if not exists reviewed_at timestamptz;
