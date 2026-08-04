package com.discordstrava.activity;

/** Fetches a single Activity after a verified webhook event. */
@FunctionalInterface
public interface StravaActivityClient {
    StravaActivity fetch(long activityId, String accessToken) throws ActivityUnavailableException;

    final class ActivityUnavailableException extends RuntimeException {
        public ActivityUnavailableException(String message, Throwable cause) { super(message, cause); }
        public ActivityUnavailableException(String message) { super(message); }
    }
}
