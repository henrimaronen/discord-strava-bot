package com.discordstrava.activity;

import java.time.Instant;

/** The sole public data allowed in an immutable Discord Activity announcement. */
public record DiscordActivityAnnouncement(String channelId, String discordMemberId, String activityName,
        String sportType, double kilometres, long movingTimeSeconds, Instant startDate, String stravaUrl,
        Double averageHeartrate, Double maxHeartrate, Integer sufferScore, String deviceName) {
}
