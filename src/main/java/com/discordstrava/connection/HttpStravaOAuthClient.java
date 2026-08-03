package com.discordstrava.connection;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** Production adapter for Strava's OAuth code exchange. It is invoked only by a verified callback. */
public final class HttpStravaOAuthClient implements StravaOAuthClient {
    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    public HttpStravaOAuthClient(String clientId, String clientSecret) {
        this(clientId, clientSecret, RestClient.create());
    }

    HttpStravaOAuthClient(String clientId, String clientSecret, RestClient restClient) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Authorization exchange(String code) throws AuthorizationException {
        try {
            Map<String, Object> response = restClient.post().uri("https://www.strava.com/api/v3/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                            + "&code=" + encode(code) + "&grant_type=authorization_code")
                    .retrieve().body(Map.class);
            if (response == null || !(response.get("athlete") instanceof Map<?, ?> athlete)) {
                throw new AuthorizationException("Strava returned an incomplete authorization");
            }
            long athleteId = number(athlete.get("id"));
            String accessToken = string(response, "access_token");
            verifyActivityRead(accessToken);
            return new Authorization(athleteId, displayName(athlete, athleteId), "activity:read",
                    accessToken, string(response, "refresh_token"),
                    Instant.ofEpochSecond(number(response.get("expires_at"))));
        } catch (AuthorizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthorizationException("Strava authorization exchange failed", exception);
        }
    }

    private static long number(Object value) throws AuthorizationException {
        if (value instanceof Number number) return number.longValue();
        throw new AuthorizationException("Strava returned an incomplete authorization");
    }

    private static String string(Map<String, Object> response, String key) throws AuthorizationException {
        Object value = response.get(key);
        if (value instanceof String string && !string.isBlank()) return string;
        throw new AuthorizationException("Strava returned an incomplete authorization");
    }

    private static String displayName(Map<?, ?> athlete, long athleteId) {
        String firstName = athlete.get("firstname") instanceof String value ? value.strip() : "";
        String lastName = athlete.get("lastname") instanceof String value ? value.strip() : "";
        String displayName = (firstName + " " + lastName).strip();
        return displayName.isBlank() ? "Strava athlete " + athleteId : displayName;
    }

    /** A successful empty activity list is still proof of activity:read for an athlete with no activities. */
    private void verifyActivityRead(String accessToken) throws AuthorizationException {
        try {
            restClient.get().uri("https://www.strava.com/api/v3/athlete/activities?per_page=1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new AuthorizationException("Strava did not grant activity:read", exception);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
