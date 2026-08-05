package com.discordstrava.connection;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public final class HttpStravaTokenRefresher implements StravaTokenRefresher {
    private final String clientId;
    private final String clientSecret;
    private final RestClient http;

    public HttpStravaTokenRefresher(String clientId, String clientSecret) {
        this(clientId, clientSecret, RestClient.create());
    }

    HttpStravaTokenRefresher(String clientId, String clientSecret, RestClient http) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = http;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RefreshedTokens refresh(String refreshToken) {
        try {
            Map<String, Object> response = http.post().uri("https://www.strava.com/api/v3/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                            + "&grant_type=refresh_token&refresh_token=" + encode(refreshToken))
                    .retrieve().body(Map.class);
            if (response == null) throw new IllegalArgumentException("empty response");
            return new RefreshedTokens(string(response, "access_token"), string(response, "refresh_token"),
                    Instant.ofEpochSecond(number(response, "expires_at")));
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized rejected) {
            throw new RefreshRejectedException("Strava rejected token refresh", rejected);
        } catch (RefreshRejectedException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new RefreshUnavailableException("Strava token refresh unavailable", failure);
        }
    }

    private static String string(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value instanceof String string && !string.isBlank()) return string;
        throw new IllegalArgumentException(key);
    }

    private static long number(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException(key);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
