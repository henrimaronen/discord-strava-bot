package com.discordstrava.configuration;

/** Application workflow for the private, server-owner channel configuration. */
public final class ConfigureAnnouncementChannel {
    private final String discordGuildId;
    private final AnnouncementConfigurationRepository repository;
    private final ChannelAccess channelAccess;

    public ConfigureAnnouncementChannel(
            String discordGuildId,
            AnnouncementConfigurationRepository repository,
            ChannelAccess channelAccess) {
        this.discordGuildId = discordGuildId;
        this.repository = repository;
        this.channelAccess = channelAccess;
    }

    public Result configureChannel(Request request) {
        return configureChannel(request, channelAccess);
    }

    public Result configureChannel(Request request, ChannelAccess requestChannelAccess) {
        Result authorization = authorize(request);
        if (authorization != null) {
            return authorization;
        }
        if (!requestChannelAccess.canPublishEmbeds(request.channelId())) {
            return Result.CHANNEL_UNAVAILABLE;
        }
        repository.save(AnnouncementConfiguration.enabled(request.channelId()));
        return Result.CONFIGURED;
    }

    public Result disable(Request request) {
        Result authorization = authorize(request);
        if (authorization != null) {
            return authorization;
        }
        repository.save(AnnouncementConfiguration.disabled());
        return Result.DISABLED;
    }

    private Result authorize(Request request) {
        if (!discordGuildId.equals(request.discordGuildId())) {
            return Result.WRONG_DISCORD_SERVER;
        }
        if (!request.canManageServer()) {
            return Result.NOT_AUTHORIZED;
        }
        return null;
    }

    public record Request(String discordGuildId, boolean canManageServer, String channelId) {
    }

    public enum Result {
        CONFIGURED,
        DISABLED,
        WRONG_DISCORD_SERVER,
        NOT_AUTHORIZED,
        CHANNEL_UNAVAILABLE
    }
}
