-- Applied once per tenant schema. There is no tenant column anywhere: the
-- schema is the boundary, so a globally unique invoice number is the correct
-- constraint here and an index on (status, issued_on) is already tenant-scoped.

create table customer (
    id         bigserial    primary key,
    name       varchar(120) not null,
    email      varchar(160) not null,
    created_at timestamptz  not null default now(),
    constraint customer_email_key unique (email)
);

create table invoice (
    id           bigserial   primary key,
    customer_id  bigint      not null,
    number       varchar(40) not null,
    status       varchar(20) not null,
    amount_cents bigint      not null,
    issued_on    date        not null,
    constraint invoice_number_key unique (number),
    constraint invoice_customer_fk foreign key (customer_id) references customer (id)
);

create index invoice_status_idx on invoice (status, issued_on);
