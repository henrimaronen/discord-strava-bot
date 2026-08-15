package com.discordstrava.activity;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** HTTP adapter intentionally projects only announcement-safe fields. */
public final class HttpStravaActivityClient implements StravaActivityClient {
    private final RestClient http;
    public HttpStravaActivityClient() { this(RestClient.create()); }
    HttpStravaActivityClient(RestClient http) { this.http = http; }
    @Override @SuppressWarnings("unchecked") public StravaActivity fetch(long activityId, String accessToken) {
        try {
            Map<String, Object> body = http.get().uri("https://www.strava.com/api/v3/activities/{id}", activityId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve().body(Map.class);
            if (body == null) throw new IllegalArgumentException("empty response");
            return new StravaActivity(number(body, "id"), string(body, "name"), string(body, "sport_type"),
                    number(body, "distance"), number(body, "moving_time"), Instant.parse(string(body, "start_date")),
                    optionalDecimal(body, "average_heartrate"), optionalDecimal(body, "max_heartrate"),
                    optionalInteger(body, "suffer_score"), optionalString(body, "device_name"));
        } catch (RuntimeException failure) { throw new ActivityUnavailableException("Strava Activity unavailable", failure); }
    }
    private static long number(Map<String, Object> body, String key) { Object value = body.get(key); if (value instanceof Number n) return n.longValue(); throw new IllegalArgumentException(key); }
    private static Double optionalDecimal(Map<String, Object> body, String key) { Object value = body.get(key); return value instanceof Number n ? n.doubleValue() : null; }
    private static Integer optionalInteger(Map<String, Object> body, String key) { Object value = body.get(key); return value instanceof Number n ? n.intValue() : null; }
    private static String optionalString(Map<String, Object> body, String key) { Object value = body.get(key); return value instanceof String s && !s.isBlank() ? s : null; }
    private static String string(Map<String, Object> body, String key) { Object value = body.get(key); if (value instanceof String s && !s.isBlank()) return s; throw new IllegalArgumentException(key); }
}
