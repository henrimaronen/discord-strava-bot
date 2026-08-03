package com.discordstrava.connection;

import java.time.Instant;

/** Port for the Strava code exchange; tests provide fakes and this app never calls it during startup. */
public interface StravaOAuthClient {
    Authorization exchange(String code) throws AuthorizationException;

    record Authorization(long athleteId, String athleteDisplayName, String grantedScope, String accessToken, String refreshToken,
                         Instant expiresAt) {
    }

    final class AuthorizationException extends Exception {
        public AuthorizationException(String message) {
            super(message);
        }

        public AuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
