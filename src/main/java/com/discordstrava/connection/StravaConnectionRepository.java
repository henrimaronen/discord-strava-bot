package com.discordstrava.connection;

import java.util.Optional;

public interface StravaConnectionRepository {
    void saveOAuthState(OAuthState state);

    Optional<OAuthState> consumeOAuthState(String value, java.time.Instant now);

    Optional<StravaConnection> findByMemberId(String discordMemberId);

    Optional<StravaConnection> findByAthleteId(long stravaAthleteId);

    Optional<StravaConnection> findById(long id);

    void save(StravaConnection connection);

    boolean rotateTokens(long connectionId, java.util.UUID connectionGeneration, String encryptedAccessToken,
            String encryptedRefreshToken, java.time.Instant expiresAt);

    void markReconnectNeeded(long stravaAthleteId);

    boolean markReconnectNeeded(long connectionId, java.util.UUID connectionGeneration);

    void saveUnlinkConfirmation(String discordMemberId, java.util.UUID connectionGeneration, java.time.Instant expiresAt);

    Optional<java.util.UUID> consumeUnlinkConfirmation(String discordMemberId, java.time.Instant now);

    boolean deleteByMemberIdAndGeneration(String discordMemberId, java.util.UUID connectionGeneration);
}
