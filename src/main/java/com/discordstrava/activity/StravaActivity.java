package com.discordstrava.activity;

import java.time.Instant;

/** The small, non-sensitive Activity projection needed for an announcement. */
public record StravaActivity(long id, String name, String sportType, double distanceMetres,
        long movingTimeSeconds, Instant startDate, Double averageHeartrate, Double maxHeartrate,
        Integer sufferScore, String deviceName, String description) {
}
