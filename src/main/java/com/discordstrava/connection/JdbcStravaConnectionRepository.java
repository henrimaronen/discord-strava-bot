package com.discordstrava.connection;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

public final class JdbcStravaConnectionRepository implements StravaConnectionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcStravaConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OAuthState> consumeOAuthState(String value, Instant now) {
        return jdbcTemplate.query("delete from strava_oauth_state where state = ? and expires_at > ? "
                        + "returning state, discord_member_id, discord_guild_id, expires_at",
                resultSet -> resultSet.next() ? Optional.of(new OAuthState(resultSet.getString(1), resultSet.getString(2),
                        resultSet.getString(3), resultSet.getTimestamp(4).toInstant())) : Optional.empty(), value, timestamp(now));
    }

    @Override
    public void saveOAuthState(OAuthState state) {
        jdbcTemplate.update("""
                insert into strava_oauth_state (state, discord_member_id, discord_guild_id, expires_at) values (?, ?, ?, ?)
                on conflict (discord_member_id) do update set state = excluded.state, discord_guild_id = excluded.discord_guild_id,
                    expires_at = excluded.expires_at
                """, state.value(), state.discordMemberId(), state.discordGuildId(), timestamp(state.expiresAt()));
    }

    @Override
    public Optional<StravaConnection> findByMemberId(String discordMemberId) {
        return find("discord_member_id", discordMemberId);
    }

    @Override
    public Optional<StravaConnection> findByAthleteId(long stravaAthleteId) {
        return find("strava_athlete_id", stravaAthleteId);
    }

    @Override
    public Optional<StravaConnection> findById(long id) {
        return find("id", id);
    }

    @Override
    public void save(StravaConnection connection) {
        try {
            jdbcTemplate.update("""
                insert into strava_connection (discord_member_id, strava_athlete_id, connection_generation, strava_athlete_display_name, granted_scope, state, connected_at,
                                               encrypted_access_token, encrypted_refresh_token, token_expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (discord_member_id) do update set
                    strava_athlete_id = excluded.strava_athlete_id, connection_generation = excluded.connection_generation, strava_athlete_display_name = excluded.strava_athlete_display_name, granted_scope = excluded.granted_scope,
                    state = excluded.state, connected_at = excluded.connected_at,
                    encrypted_access_token = excluded.encrypted_access_token,
                    encrypted_refresh_token = excluded.encrypted_refresh_token, token_expires_at = excluded.token_expires_at
                """, connection.discordMemberId(), connection.stravaAthleteId(), connection.generation(), connection.stravaAthleteDisplayName(), connection.grantedScope(),
                connection.state().name(), timestamp(connection.connectedAt()), connection.encryptedAccessToken(),
                connection.encryptedRefreshToken(), timestamp(connection.tokenExpiresAt()));
        } catch (DuplicateKeyException exception) {
            throw new AthleteAlreadyLinkedException(exception);
        }
    }

    @Override
    public void markReconnectNeeded(long stravaAthleteId) {
        jdbcTemplate.update("update strava_connection set state = 'RECONNECT_NEEDED', encrypted_access_token = null, "
                        + "encrypted_refresh_token = null, token_expires_at = null where strava_athlete_id = ?", stravaAthleteId);
    }

    @Override
    public boolean markReconnectNeeded(long connectionId, UUID connectionGeneration) {
        return jdbcTemplate.update("update strava_connection set state = 'RECONNECT_NEEDED', encrypted_access_token = null, "
                        + "encrypted_refresh_token = null, token_expires_at = null where id = ? and connection_generation = ? and state = 'ACTIVE'",
                connectionId, connectionGeneration) == 1;
    }

    @Override
    public boolean rotateTokens(long connectionId, UUID connectionGeneration, String encryptedAccessToken,
            String encryptedRefreshToken, Instant expiresAt) {
        return jdbcTemplate.update("update strava_connection set encrypted_access_token = ?, encrypted_refresh_token = ?, "
                        + "token_expires_at = ? where id = ? and connection_generation = ? and state = 'ACTIVE'",
                encryptedAccessToken, encryptedRefreshToken, timestamp(expiresAt), connectionId, connectionGeneration) == 1;
    }

    @Override
    public void saveUnlinkConfirmation(String discordMemberId, UUID connectionGeneration, Instant expiresAt) {
        jdbcTemplate.update("insert into strava_unlink_confirmation (discord_member_id, connection_generation, expires_at) values (?, ?, ?) "
                        + "on conflict (discord_member_id) do update set connection_generation = excluded.connection_generation, expires_at = excluded.expires_at",
                discordMemberId, connectionGeneration, timestamp(expiresAt));
    }

    @Override
    public Optional<UUID> consumeUnlinkConfirmation(String discordMemberId, Instant now) {
        return jdbcTemplate.query("delete from strava_unlink_confirmation where discord_member_id = ? and expires_at > ? returning connection_generation",
                resultSet -> resultSet.next() ? Optional.of(resultSet.getObject(1, UUID.class)) : Optional.empty(), discordMemberId, timestamp(now));
    }

    @Override
    public boolean deleteByMemberIdAndGeneration(String discordMemberId, UUID connectionGeneration) {
        return jdbcTemplate.update("delete from strava_connection where discord_member_id = ? and connection_generation = ?", discordMemberId, connectionGeneration) == 1;
    }

    private Optional<StravaConnection> find(String column, Object value) {
        return jdbcTemplate.query("select id, discord_member_id, strava_athlete_id, connection_generation, strava_athlete_display_name, granted_scope, state, connected_at, "
                        + "encrypted_access_token, encrypted_refresh_token, token_expires_at from strava_connection where " + column + " = ?",
                (ResultSet resultSet) -> resultSet.next() ? Optional.of(row(resultSet)) : Optional.empty(), value);
    }

    private StravaConnection row(ResultSet resultSet) throws java.sql.SQLException {
        return new StravaConnection(resultSet.getLong(1), resultSet.getString(2), resultSet.getLong(3), resultSet.getObject(4, UUID.class), resultSet.getString(5),
                resultSet.getString(6), ConnectionState.valueOf(resultSet.getString(7)), resultSet.getTimestamp(8).toInstant(), resultSet.getString(9),
                resultSet.getString(10), resultSet.getTimestamp(11) == null ? null : resultSet.getTimestamp(11).toInstant());
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
