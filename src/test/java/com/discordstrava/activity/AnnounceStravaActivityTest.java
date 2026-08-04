package com.discordstrava.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.discordstrava.configuration.AnnouncementConfiguration;
import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.connection.ConnectionState;
import com.discordstrava.connection.OAuthState;
import com.discordstrava.connection.StravaConnection;
import com.discordstrava.connection.StravaConnectionRepository;
import com.discordstrava.connection.TokenCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnnounceStravaActivityTest {
    private static final Instant CONNECTED = Instant.parse("2026-08-04T10:00:00Z");
    private final TokenCipher tokens = new TokenCipher("test-key");
    private final FakeConnections connections = new FakeConnections();
    private final FakeDeliveries deliveries = new FakeDeliveries();
    private final FakeStrava strava = new FakeStrava();
    private final FakeDiscord discord = new FakeDiscord();
    private final AnnounceStravaActivity service = new AnnounceStravaActivity(connections,
            new FakeConfiguration(), deliveries, strava, discord, tokens);

    @Test
    void announcesOneEligibleCreateWithOnlyTheAllowedProjection() {
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.ANNOUNCED);

        assertThat(discord.sent).singleElement().satisfies(sent -> {
            assertThat(sent.channelId()).isEqualTo("channel");
            assertThat(sent.discordMemberId()).isEqualTo("member");
            assertThat(sent.activityName()).isEqualTo("Morning Run");
            assertThat(sent.sportType()).isEqualTo("Run");
            assertThat(sent.kilometres()).isEqualTo(5.2);
            assertThat(sent.movingTimeSeconds()).isEqualTo(1800);
            assertThat(sent.stravaUrl()).isEqualTo("https://www.strava.com/activities/44");
        });
        assertThat(deliveries.sent).containsExactly(44L);
    }

    @Test
    void ignores_nonCreatesInactiveConnectionsAndPreConnectionActivities() {
        assertThat(service.announce(new ActivityWebhook("activity", "update", 44, 7, 1)))
                .isEqualTo(AnnounceStravaActivity.Result.IGNORED);
        connections.connection = reconnectNeeded();
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.IGNORED);
        connections.connection = active();
        strava.activity = new StravaActivity(44, "Old", "Run", 1, 1, CONNECTED);
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.INELIGIBLE);
        assertThat(discord.sent).isEmpty();
    }

    @Test
    void duplicateActivityIdsSendOnlyOnce() {
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.ANNOUNCED);
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.DUPLICATE);
        assertThat(discord.sent).hasSize(1);
    }

    private static ActivityWebhook event() { return new ActivityWebhook("activity", "create", 44, 7, 1); }
    private StravaConnection active() { return new StravaConnection(3, "member", 7, UUID.randomUUID(), "private", "activity:read", ConnectionState.ACTIVE, CONNECTED, tokens.encrypt("access"), tokens.encrypt("refresh"), CONNECTED.plusSeconds(1)); }
    private StravaConnection reconnectNeeded() { return StravaConnection.reconnectNeeded(active()); }
    private final class FakeConnections implements StravaConnectionRepository {
        private StravaConnection connection = active();
        public void saveOAuthState(OAuthState state) { }
        public Optional<OAuthState> consumeOAuthState(String value, Instant now) { return Optional.empty(); }
        public Optional<StravaConnection> findByMemberId(String member) { return Optional.empty(); }
        public Optional<StravaConnection> findByAthleteId(long athlete) { return athlete == 7 ? Optional.of(connection) : Optional.empty(); }
        public void save(StravaConnection connection) { }
        public void markReconnectNeeded(long athlete) { }
        public void saveUnlinkConfirmation(String member, UUID generation, Instant expiry) { }
        public Optional<UUID> consumeUnlinkConfirmation(String member, Instant now) { return Optional.empty(); }
        public boolean deleteByMemberIdAndGeneration(String member, UUID generation) { return false; }
    }
    private static final class FakeConfiguration implements AnnouncementConfigurationRepository {
        public AnnouncementConfiguration get() { return AnnouncementConfiguration.enabled("channel"); }
        public void save(AnnouncementConfiguration configuration) { }
    }
    private static final class FakeDeliveries implements ActivityDeliveryRepository {
        private final Set<Long> claimed = new HashSet<>(); private final Set<Long> sent = new HashSet<>();
        public boolean claim(long activityId, long connectionId) { return claimed.add(activityId); }
        public void markSent(long activityId) { sent.add(activityId); }
    }
    private static final class FakeStrava implements StravaActivityClient {
        private StravaActivity activity = new StravaActivity(44, "Morning Run", "Run", 5200, 1800, CONNECTED.plusSeconds(1));
        public StravaActivity fetch(long id, String token) { return activity; }
    }
    private static final class FakeDiscord implements DiscordActivityAnnouncements {
        private final java.util.List<DiscordActivityAnnouncement> sent = new java.util.ArrayList<>();
        public void send(DiscordActivityAnnouncement announcement) { sent.add(announcement); }
    }
}
