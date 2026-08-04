package com.discordstrava.activity;

import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.connection.ConnectionState;
import com.discordstrava.connection.StravaConnection;
import com.discordstrava.connection.StravaConnectionRepository;
import com.discordstrava.connection.TokenCipher;

/** Application workflow for one create event. Non-create events deliberately have no effects. */
public final class AnnounceStravaActivity {
    private final StravaConnectionRepository connections;
    private final AnnouncementConfigurationRepository configuration;
    private final ActivityDeliveryRepository deliveries;
    private final StravaActivityClient strava;
    private final DiscordActivityAnnouncements discord;
    private final TokenCipher tokens;

    public AnnounceStravaActivity(StravaConnectionRepository connections,
            AnnouncementConfigurationRepository configuration, ActivityDeliveryRepository deliveries,
            StravaActivityClient strava, DiscordActivityAnnouncements discord, TokenCipher tokens) {
        this.connections = connections;
        this.configuration = configuration;
        this.deliveries = deliveries;
        this.strava = strava;
        this.discord = discord;
        this.tokens = tokens;
    }

    public Result announce(ActivityWebhook event) {
        if (!event.isActivityCreate()) return Result.IGNORED;
        var configured = configuration.get();
        if (!configured.enabled()) return Result.IGNORED;
        var connection = connections.findByAthleteId(event.athleteId());
        if (connection.isEmpty() || connection.get().state() != ConnectionState.ACTIVE) return Result.IGNORED;
        StravaConnection active = connection.get();
        if (!deliveries.claim(event.activityId(), active.id())) return Result.DUPLICATE;
        StravaActivity activity = strava.fetch(event.activityId(), tokens.decrypt(active.encryptedAccessToken()));
        if (activity.id() != event.activityId() || !activity.startDate().isAfter(active.connectedAt())) return Result.INELIGIBLE;
        discord.send(new DiscordActivityAnnouncement(configured.channelId(), active.discordMemberId(), activity.name(),
                activity.sportType(), activity.distanceMetres() / 1000.0, activity.movingTimeSeconds(),
                activity.startDate(), "https://www.strava.com/activities/" + activity.id()));
        deliveries.markSent(activity.id());
        return Result.ANNOUNCED;
    }

    public enum Result { ANNOUNCED, DUPLICATE, INELIGIBLE, IGNORED }
}
