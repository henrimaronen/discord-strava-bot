package com.discordstrava.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

public final class RuntimeSettingsLoader {
    private static final Pattern SNOWFLAKE = Pattern.compile("[0-9]{15,22}");
    private static final String[] REQUIRED = {
            "DATABASE_URL", "DISCORD_TOKEN", "DISCORD_GUILD_ID", "STRAVA_CLIENT_ID",
            "STRAVA_CLIENT_SECRET", "STRAVA_WEBHOOK_VERIFY_TOKEN", "STRAVA_WEBHOOK_SUBSCRIPTION_ID",
            "OAUTH_TOKEN_ENCRYPTION_KEY", "PUBLIC_BASE_URL"
    };

    private RuntimeSettingsLoader() {
    }

    public static RuntimeSettings load(Environment environment) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : REQUIRED) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required configuration: " + key);
            }
            values.put(key, value.trim());
        }
        String guildId = values.get("DISCORD_GUILD_ID");
        if (!SNOWFLAKE.matcher(guildId).matches()) {
            throw new IllegalStateException("DISCORD_GUILD_ID must be a Discord snowflake");
        }
        String baseUrl = values.get("PUBLIC_BASE_URL");
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalStateException("PUBLIC_BASE_URL must use https");
        }
        return new RuntimeSettings(
                values.get("DATABASE_URL"), values.get("DISCORD_TOKEN"), guildId,
                values.get("STRAVA_CLIENT_ID"), values.get("STRAVA_CLIENT_SECRET"),
                values.get("STRAVA_WEBHOOK_VERIFY_TOKEN"), values.get("STRAVA_WEBHOOK_SUBSCRIPTION_ID"),
                values.get("OAUTH_TOKEN_ENCRYPTION_KEY"), baseUrl);
    }
}
