CREATE TABLE IF NOT EXISTS event.outbox (
    id             varchar(36)  not null,
    transaction_id char(36)     not null,
    aggregate_type varchar(100) not null,
    partition_key  varchar(100) not null,
    payload        json         not null,
    event_type     varchar(100) not null,
    domain_type    varchar(100) not null,
    dispatch_type  varchar(100) not null,
    status         char(20)     not null,
    retry_cnt      int          not null,
    created_at     timestamp(3) default current_timestamp(3),
    updated_at     timestamp(3) default current_timestamp(3) on update current_timestamp(3),
    primary key (id),
    index idx_outbox_dispatch_type_status_created_at (dispatch_type, status, created_at)
);

CREATE TABLE IF NOT EXISTS event.dlq (
   id              varchar(26)  not null,
    source_id      varchar(100) not null,
    event_type     varchar(100) not null,
    aggregate_id   varchar(255) null,
    aggregate_type varchar(100) null,
    transaction_id varchar(26)  null,
    domain_type    varchar(50)  null,
    status         varchar(30)  not null,
    error_message  varchar(1000) null,
    payload        json         not null,
    created_at     timestamp(3) default current_timestamp(3),
    updated_at     timestamp(3) default current_timestamp(3) on update current_timestamp(3),
    primary key (id),
    index idx_dlq_status_created_at (status, created_at)
);

CREATE TABLE IF NOT EXISTS event.inbox (
    id            varchar(191) not null,
    consumer_name varchar(100) not null,
    event_id      varchar(64)  not null,
    created_at    timestamp(3) default current_timestamp(3),
    updated_at    timestamp(3) default current_timestamp(3) on update current_timestamp(3),
    primary key (id),
    constraint uk_inbox_consumer_event unique (consumer_name, event_id)
);
