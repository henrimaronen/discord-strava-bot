package com.discordstrava.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrimaryPhotoTest {
    private static final String LARGE = "https://dgtzuqphqg23d.cloudfront.net/photo-600x450.jpg";
    private static final String SMALL = "https://dgtzuqphqg23d.cloudfront.net/photo-100x75.jpg";

    @Test
    void prefersThe600Url() {
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(urls(Map.of("600", LARGE, "100", SMALL))))).isEqualTo(LARGE);
    }

    @Test
    void fallsBackToThe100Url() {
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(urls(Map.of("100", SMALL))))).isEqualTo(SMALL);
    }

    @Test
    void missingPhotosObjectHasNoPhoto() {
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(Map.of("name", "Morning Run"))).isNull();
    }

    @Test
    void nullPrimaryHasNoPhoto() {
        Map<String, Object> photos = new HashMap<>();
        photos.put("primary", null);
        photos.put("count", 0);
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(Map.of("photos", photos))).isNull();
    }

    @Test
    void blank600WithNo100HasNoPhoto() {
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(urls(Map.of("600", "  "))))).isNull();
    }

    @Test
    void nonStringUrlHasNoPhotoAndDoesNotThrow() {
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(urls(Map.of("600", 42))))).isNull();
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(Map.of("photos", "not a map"))).isNull();
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(Map.of("urls", "not a map")))).isNull();
    }

    @Test
    void onlyHttpsUrlsBecomePhotos() {
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(urls(Map.of("600", "http://example.com/photo.jpg"))))).isNull();
        assertThat(HttpStravaActivityClient.primaryPhotoUrl(body(urls(Map.of("600", "ftp://example.com/photo.jpg"))))).isNull();
    }

    private static Map<String, Object> urls(Map<String, Object> urls) { return Map.of("urls", urls); }

    private static Map<String, Object> body(Map<String, Object> primary) { return Map.of("photos", Map.of("primary", primary)); }
}
