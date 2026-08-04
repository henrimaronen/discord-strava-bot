create table activity_delivery (
    activity_id bigint primary key,
    connection_id bigint not null references strava_connection(id) on delete cascade,
    state varchar(16) not null check (state in ('PROCESSING', 'SENT')),
    created_at timestamptz not null default current_timestamp,
    sent_at timestamptz
);
