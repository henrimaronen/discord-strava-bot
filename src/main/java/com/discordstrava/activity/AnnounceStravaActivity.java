package com.discordstrava.activity;

import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.connection.ConnectionState;
import com.discordstrava.connection.StravaConnection;
import com.discordstrava.connection.StravaConnectionRepository;
import com.discordstrava.connection.TokenCipher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application workflow for one create event. Non-create events deliberately have no effects. */
public final class AnnounceStravaActivity {
    private static final Logger log = LoggerFactory.getLogger(AnnounceStravaActivity.class);
    private static final Duration RETRY_WINDOW = Duration.ofHours(24);
    private final StravaConnectionRepository connections;
    private final AnnouncementConfigurationRepository configuration;
    private final ActivityDeliveryRepository deliveries;
    private final StravaActivityClient strava;
    private final DiscordActivityAnnouncements discord;
    private final TokenCipher tokens;
    private final Clock clock;

    public AnnounceStravaActivity(StravaConnectionRepository connections,
            AnnouncementConfigurationRepository configuration, ActivityDeliveryRepository deliveries,
            StravaActivityClient strava, DiscordActivityAnnouncements discord, TokenCipher tokens, Clock clock) {
        this.connections = connections;
        this.configuration = configuration;
        this.deliveries = deliveries;
        this.strava = strava;
        this.discord = discord;
        this.tokens = tokens;
        this.clock = clock;
    }

    public Result announce(ActivityWebhook event) {
        Result queued = enqueue(event);
        return queued == Result.QUEUED ? deliver(event.activityId()) : queued;
    }

    /** Claims delivery synchronously, before the HTTP webhook receiver acknowledges the provider. */
    public Result enqueue(ActivityWebhook event) {
        if (!event.isActivityCreate()) {
            log.info("[DEBUG-webhook] ignored non-create event");
            return Result.IGNORED;
        }
        var configured = configuration.get();
        if (!configured.enabled()) {
            log.info("[DEBUG-webhook] ignored because announcements are disabled");
            return Result.IGNORED;
        }
        var connection = connections.findByAthleteId(event.athleteId());
        if (connection.isEmpty() || connection.get().state() != ConnectionState.ACTIVE) {
            log.info("[DEBUG-webhook] ignored because athlete connection is unavailable");
            return Result.IGNORED;
        }
        StravaConnection active = connection.get();
        boolean claimed = deliveries.claim(event.activityId(), active.id(), clock.instant());
        if (!claimed) log.info("[DEBUG-webhook] ignored duplicate activity delivery");
        return claimed ? Result.QUEUED : Result.DUPLICATE;
    }

    /** Processes one persisted record. It is safe for an immediate webhook task and the periodic retry worker to race. */
    public Result deliver(long activityId) {
        Instant now = clock.instant();
        if (!deliveries.start(activityId, now)) return Result.DUPLICATE;
        return deliverClaimed(activityId, now);
    }

    private Result deliverClaimed(long activityId, Instant now) {
        ActivityDeliveryRepository.Delivery delivery = deliveries.delivery(activityId).orElseThrow();
        var connection = connections.findById(delivery.connectionId());
        if (connection.isEmpty() || connection.get().state() != ConnectionState.ACTIVE) {
            deliveries.discard(activityId);
            return Result.IGNORED;
        }
        StravaConnection active = connection.get();
        var configured = configuration.get();
        if (!configured.enabled()) {
            deliveries.discard(activityId);
            return Result.IGNORED;
        }
        try {
            StravaActivity activity = strava.fetch(activityId, tokens.decrypt(active.encryptedAccessToken()));
            if (activity.id() != activityId || !activity.startDate().isAfter(active.connectedAt())) {
                deliveries.discard(activityId);
                return Result.INELIGIBLE;
            }
            discord.send(new DiscordActivityAnnouncement(configured.channelId(), active.discordMemberId(), activity.name(),
                    activity.sportType(), activity.distanceMetres() / 1000.0, activity.movingTimeSeconds(),
                    activity.startDate(), "https://www.strava.com/activities/" + activity.id()));
            try {
                deliveries.markSent(activity.id(), now);
            } catch (RuntimeException markFailure) {
                // The message may have reached Discord. Never turn this into a blind retry and duplicate it.
                deliveries.markUncertain(activityId);
                log.warn("activity_delivery_uncertain_sent activity_id={} connection_id={}", activityId, delivery.connectionId());
                return Result.UNCERTAIN_SENT;
            }
            return Result.ANNOUNCED;
        } catch (DiscordDeliveryException failure) {
            if (failure.kind() == DiscordDeliveryException.Kind.MEMBER_GONE) {
                connections.deleteByMemberIdAndGeneration(active.discordMemberId(), active.generation());
                return Result.MEMBER_GONE;
            }
            if (failure.kind() == DiscordDeliveryException.Kind.CHANNEL_UNAVAILABLE) {
                configuration.save(com.discordstrava.configuration.AnnouncementConfiguration.disabled());
                deliveries.markFailed(activityId);
                log.warn("announcement_channel_unavailable connection_id={}", active.id());
                return Result.CHANNEL_UNAVAILABLE;
            }
            return retryOrFail(activityId, delivery, now);
        } catch (StravaActivityClient.ActivityUnavailableException failure) {
            return retryOrFail(activityId, delivery, now);
        } catch (RuntimeException failure) {
            return retryOrFail(activityId, delivery, now);
        }
    }

    private Result retryOrFail(long activityId, ActivityDeliveryRepository.Delivery delivery, Instant now) {
        Instant deadline = delivery.createdAt().plus(RETRY_WINDOW);
        if (!now.isBefore(deadline)) {
            deliveries.markFailed(activityId);
            log.warn("activity_delivery_exhausted activity_id={} connection_id={}", activityId, delivery.connectionId());
            return Result.FAILED;
        }
        long seconds = Math.min(3600, 1L << Math.min(20, delivery.attempts()));
        deliveries.retry(activityId, now.plusSeconds(seconds).isAfter(deadline) ? deadline : now.plusSeconds(seconds));
        return Result.RETRY_SCHEDULED;
    }

    public enum Result { QUEUED, ANNOUNCED, DUPLICATE, INELIGIBLE, IGNORED, RETRY_SCHEDULED, FAILED, MEMBER_GONE, CHANNEL_UNAVAILABLE, UNCERTAIN_SENT }
}
