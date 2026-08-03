create table announcement_configuration (
    id smallint primary key check (id = 1),
    announcement_channel_id varchar(22),
    enabled boolean not null,
    check ((enabled and announcement_channel_id is not null) or (not enabled and announcement_channel_id is null))
);
