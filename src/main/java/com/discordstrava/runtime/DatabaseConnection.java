package com.discordstrava.runtime;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Converts platform Postgres URLs to the JDBC connection settings Hikari expects. */
record DatabaseConnection(String jdbcUrl, String username, String password) {
    static DatabaseConnection from(String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            return new DatabaseConnection(databaseUrl, null, null);
        }

        URI uri = URI.create(databaseUrl);
        if (!"postgresql".equals(uri.getScheme()) && !"postgres".equals(uri.getScheme())) {
            throw new IllegalStateException("DATABASE_URL must be a PostgreSQL URL");
        }
        if (uri.getHost() == null || uri.getRawPath() == null || uri.getRawPath().isBlank()) {
            throw new IllegalStateException("DATABASE_URL must include a PostgreSQL host and database");
        }

        String host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
        String jdbcUrl = "jdbc:postgresql://" + host
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        String[] credentials = credentials(uri.getRawUserInfo());
        return new DatabaseConnection(jdbcUrl, credentials[0], credentials[1]);
    }

    private static String[] credentials(String rawUserInfo) {
        if (rawUserInfo == null) {
            return new String[] {null, null};
        }
        int separator = rawUserInfo.indexOf(':');
        String rawUsername = separator == -1 ? rawUserInfo : rawUserInfo.substring(0, separator);
        String rawPassword = separator == -1 ? null : rawUserInfo.substring(separator + 1);
        return new String[] {decode(rawUsername), rawPassword == null ? null : decode(rawPassword)};
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
