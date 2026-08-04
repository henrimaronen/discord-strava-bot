package com.discordstrava.activity;

/** Resolves the member's current Discord display name and sends one immutable embed. */
@FunctionalInterface
public interface DiscordActivityAnnouncements {
    void send(DiscordActivityAnnouncement announcement);
}
