package com.discordstrava.connection;

import java.time.Instant;

public record OAuthState(String value, String discordMemberId, String discordGuildId, Instant expiresAt) {
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
