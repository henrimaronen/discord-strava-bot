package com.discordstrava.connection;

import java.time.Instant;

public interface StravaTokenRefresher {
    RefreshedTokens refresh(String refreshToken);

    record RefreshedTokens(String accessToken, String refreshToken, Instant expiresAt) { }

    final class RefreshRejectedException extends RuntimeException {
        public RefreshRejectedException(String message, Throwable cause) { super(message, cause); }
    }

    final class RefreshUnavailableException extends RuntimeException {
        public RefreshUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
