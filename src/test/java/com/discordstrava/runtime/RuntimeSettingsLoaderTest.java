package com.discordstrava.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeSettingsLoaderTest {
    @Test
    void rejectsMissingRequiredConfiguration() {
        assertThatThrownBy(() -> RuntimeSettingsLoader.load(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing required configuration: DATABASE_URL");
    }

    @Test
    void acceptsCompleteRailwayConfiguration() {
        RuntimeSettings settings = RuntimeSettingsLoader.load(environment(validSettings()));

        assertThat(settings.discordGuildId()).isEqualTo("123456789012345678");
        assertThat(settings.publicBaseUrl()).isEqualTo("https://bot.example.com");
    }

    @Test
    void rejectsNonHttpsCallbackBaseUrl() {
        Map<String, String> values = validSettings();
        values.put("PUBLIC_BASE_URL", "http://bot.example.com");

        assertThatThrownBy(() -> RuntimeSettingsLoader.load(environment(values)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PUBLIC_BASE_URL must use https");
    }

    @Test
    void rejectsInvalidDiscordGuildId() {
        Map<String, String> values = validSettings();
        values.put("DISCORD_GUILD_ID", "not-a-snowflake");

        assertThatThrownBy(() -> RuntimeSettingsLoader.load(environment(values)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DISCORD_GUILD_ID must be a Discord snowflake");
    }

    private MockEnvironment environment(Map<String, String> values) {
        MockEnvironment environment = new MockEnvironment();
        values.forEach(environment::setProperty);
        return environment;
    }

    private Map<String, String> validSettings() {
        return new java.util.HashMap<>(Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost:5432/bot",
                "DISCORD_TOKEN", "discord-token",
                "DISCORD_GUILD_ID", "123456789012345678",
                "STRAVA_CLIENT_ID", "12345",
                "STRAVA_CLIENT_SECRET", "strava-secret",
                "STRAVA_WEBHOOK_VERIFY_TOKEN", "verify-token",
                "STRAVA_WEBHOOK_SUBSCRIPTION_ID", "1",
                "OAUTH_TOKEN_ENCRYPTION_KEY", "encryption-key",
                "PUBLIC_BASE_URL", "https://bot.example.com"));
    }
}
