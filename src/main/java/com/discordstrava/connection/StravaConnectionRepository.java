package com.discordstrava.connection;

import java.util.Optional;

public interface StravaConnectionRepository {
    void saveOAuthState(OAuthState state);

    Optional<OAuthState> consumeOAuthState(String value, java.time.Instant now);

    Optional<StravaConnection> findByMemberId(String discordMemberId);

    Optional<StravaConnection> findByAthleteId(long stravaAthleteId);

    void save(StravaConnection connection);

    void markReconnectNeeded(long stravaAthleteId);

    void saveUnlinkConfirmation(String discordMemberId, java.util.UUID connectionGeneration, java.time.Instant expiresAt);

    Optional<java.util.UUID> consumeUnlinkConfirmation(String discordMemberId, java.time.Instant now);

    boolean deleteByMemberIdAndGeneration(String discordMemberId, java.util.UUID connectionGeneration);
}
