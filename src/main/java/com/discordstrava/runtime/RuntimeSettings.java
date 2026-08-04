package com.discordstrava.runtime;

/** Runtime-only configuration. Values are read from environment-backed Spring properties. */
public record RuntimeSettings(
        String databaseUrl,
        String discordToken,
        String discordGuildId,
        String stravaClientId,
        String stravaClientSecret,
        String stravaWebhookVerifyToken,
        String stravaWebhookSubscriptionId,
        String oauthTokenEncryptionKey,
        String publicBaseUrl) {
}
