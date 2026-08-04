package com.discordstrava.discord;

/** User-facing command reference for the Discord bot. */
final class DiscordHelp {
    private DiscordHelp() {
    }

    static String message() {
        return "Discord Strava Bot commands:\n"
                + "/connect — connect your Strava account\n"
                + "/status — show your Strava connection status\n"
                + "/unlink — disconnect your Strava account\n"
                + "/configure channel — set the activity announcement channel (Manage Server)\n"
                + "/configure disable — disable activity announcements (Manage Server)";
    }
}
