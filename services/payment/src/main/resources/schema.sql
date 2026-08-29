create table if not exists payments (
    id         varchar(36)    not null primary key,
    order_id   varchar(36)    not null unique,
    amount     numeric(12, 2) not null,
    currency   varchar(3)     not null,
    status     varchar(16)    not null,
    created_at timestamp      not null,
    updated_at timestamp      not null
);
