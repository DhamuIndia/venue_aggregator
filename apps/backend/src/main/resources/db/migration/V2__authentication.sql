alter table users
    add column if not exists password_hash varchar(255);

update users
set password_hash = '{NO_LOGIN}'
where password_hash is null;

alter table users
    alter column password_hash set not null,
    alter column email drop not null;

create unique index if not exists uq_users_phone
    on users(phone)
    where phone is not null;
