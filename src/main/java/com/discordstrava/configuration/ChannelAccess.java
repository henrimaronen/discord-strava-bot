package com.discordstrava.configuration;

/** Checks whether the bot can publish an embedded Activity announcement to a channel. */
@FunctionalInterface
public interface ChannelAccess {
    boolean canPublishEmbeds(String channelId);
}
