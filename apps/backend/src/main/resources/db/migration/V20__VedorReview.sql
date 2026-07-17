create table vendor_reviews (
    id bigserial primary key,

    vendor_lead_id bigint not null,
    vendor_id bigint not null,
    customer_user_id bigint not null,

    rating integer not null,
    comment varchar(500) not null,

    active boolean not null default true,
    moderation_status varchar(30) not null default 'PENDING',

    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,

    constraint fk_vendor_review_lead
        foreign key (vendor_lead_id)
        references vendor_leads(id),

    constraint fk_vendor_review_vendor
        foreign key (vendor_id)
        references vendors(id),

    constraint fk_vendor_review_customer
        foreign key (customer_user_id)
        references users(id)
);

create unique index uq_vendor_review_lead
on vendor_reviews(vendor_lead_id);
