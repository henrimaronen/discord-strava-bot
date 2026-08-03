package com.discordstrava.configuration;

/** The one Announcement channel for the configured Discord server. */
public record AnnouncementConfiguration(String channelId, boolean enabled) {
    public AnnouncementConfiguration {
        if (enabled && (channelId == null || channelId.isBlank())) {
            throw new IllegalArgumentException("An enabled announcement configuration needs a channel");
        }
        if (!enabled) {
            channelId = null;
        }
    }

    public static AnnouncementConfiguration enabled(String channelId) {
        return new AnnouncementConfiguration(channelId, true);
    }

    public static AnnouncementConfiguration disabled() {
        return new AnnouncementConfiguration(null, false);
    }
}
