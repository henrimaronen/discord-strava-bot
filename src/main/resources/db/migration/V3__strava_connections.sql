create table strava_connection (
    id bigserial primary key,
    discord_member_id varchar(22) not null unique,
    strava_athlete_id bigint not null unique,
    connection_generation uuid not null,
    granted_scope varchar(255) not null,
    state varchar(32) not null check (state in ('ACTIVE', 'RECONNECT_NEEDED')),
    connected_at timestamptz not null,
    strava_athlete_display_name varchar(255),
    encrypted_access_token text,
    encrypted_refresh_token text,
    token_expires_at timestamptz,
    check (
        (state = 'ACTIVE' and encrypted_access_token is not null and encrypted_refresh_token is not null and token_expires_at is not null)
        or (state = 'RECONNECT_NEEDED' and encrypted_access_token is null and encrypted_refresh_token is null and token_expires_at is null)
    )
);

create table strava_oauth_state (
    state varchar(128) primary key,
    discord_member_id varchar(22) not null unique,
    discord_guild_id varchar(22) not null,
    expires_at timestamptz not null
);

create table strava_unlink_confirmation (
    discord_member_id varchar(22) primary key,
    connection_generation uuid not null,
    expires_at timestamptz not null
);
