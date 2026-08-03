package com.discordstrava.connection;

import java.time.Instant;
import java.util.UUID;

/** Durable, private authorization link between a Discord member and a Strava Athlete. */
public record StravaConnection(
        long id,
        String discordMemberId,
        long stravaAthleteId,
        UUID generation,
        String stravaAthleteDisplayName,
        String grantedScope,
        ConnectionState state,
        Instant connectedAt,
        String encryptedAccessToken,
        String encryptedRefreshToken,
        Instant tokenExpiresAt) {
    public static StravaConnection active(
            String discordMemberId, long stravaAthleteId, String stravaAthleteDisplayName, String grantedScope, Instant connectedAt,
            String encryptedAccessToken, String encryptedRefreshToken, Instant tokenExpiresAt) {
        return new StravaConnection(0, discordMemberId, stravaAthleteId, UUID.randomUUID(), stravaAthleteDisplayName, grantedScope, ConnectionState.ACTIVE,
                connectedAt, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
    }

    public static StravaConnection reconnectNeeded(StravaConnection connection) {
        return new StravaConnection(connection.id, connection.discordMemberId, connection.stravaAthleteId, connection.generation, connection.stravaAthleteDisplayName,
                connection.grantedScope, ConnectionState.RECONNECT_NEEDED, connection.connectedAt, null, null, null);
    }
}
