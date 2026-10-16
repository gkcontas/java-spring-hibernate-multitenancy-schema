-- The shared schema holds two things: the tenant registry, which is the only
-- place that knows a tenant exists, and the discriminator-column model, where
-- every row of every tenant lives in the same table.

create table tenant_registry (
    tenant_id    varchar(40)  primary key,
    schema_name  varchar(63)  not null unique,
    display_name varchar(120) not null,
    created_at   timestamptz  not null default now()
);

create table customer (
    id         bigserial    primary key,
    tenant_id  varchar(40)  not null,
    name       varchar(120) not null,
    email      varchar(160) not null,
    created_at timestamptz  not null default now(),
    -- A plain primary key on id cannot express "this customer belongs to this
    -- tenant", so a foreign key pointing at it cannot be tenant-aware either.
    -- This redundant-looking unique key is what makes the composite foreign
    -- key on invoice possible.
    constraint customer_tenant_id_key unique (tenant_id, id)
);

create index customer_tenant_idx on customer (tenant_id);

-- The tenant-aware version: the discriminator leads every index and every
-- constraint it takes part in.
create table invoice (
    id           bigserial   primary key,
    tenant_id    varchar(40) not null,
    customer_id  bigint      not null,
    number       varchar(40) not null,
    status       varchar(20) not null,
    amount_cents bigint      not null,
    issued_on    date        not null,
    constraint invoice_tenant_number_key unique (tenant_id, number),
    constraint invoice_customer_fk foreign key (tenant_id, customer_id)
        references customer (tenant_id, id)
);

create index invoice_tenant_status_idx on invoice (tenant_id, status, issued_on);

-- The same table written the way it comes out when the discriminator is treated
-- as just one more column: the unique key is global, the foreign key ignores
-- the tenant, and the index for the most common filter does not start with it.
create table invoice_naive (
    id           bigserial   primary key,
    tenant_id    varchar(40) not null,
    customer_id  bigint      not null,
    number       varchar(40) not null,
    status       varchar(20) not null,
    amount_cents bigint      not null,
    issued_on    date        not null,
    constraint invoice_naive_number_key unique (number),
    constraint invoice_naive_customer_fk foreign key (customer_id)
        references customer (id)
);

create index invoice_naive_status_idx on invoice_naive (status, issued_on);
