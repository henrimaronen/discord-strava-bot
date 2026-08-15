package com.discordstrava.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.discordstrava.configuration.AnnouncementConfiguration;
import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.connection.ConnectionState;
import com.discordstrava.connection.OAuthState;
import com.discordstrava.connection.StravaConnection;
import com.discordstrava.connection.StravaConnectionRepository;
import com.discordstrava.connection.StravaTokenRefresher;
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
    private final FakeTokenRefresher tokenRefresher = new FakeTokenRefresher();
    private final AnnounceStravaActivity service = new AnnounceStravaActivity(connections,
            new FakeConfiguration(), deliveries, strava, discord, tokens, tokenRefresher, Clock.fixed(CONNECTED, ZoneOffset.UTC));

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
            assertThat(sent.averageHeartrate()).isEqualTo(140.3);
            assertThat(sent.maxHeartrate()).isEqualTo(178.0);
            assertThat(sent.sufferScore()).isEqualTo(82);
            assertThat(sent.deviceName()).isEqualTo("Garmin Forerunner");
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
        strava.activity = new StravaActivity(44, "Old", "Run", 1, 1, CONNECTED, null, null, null, null);
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.INELIGIBLE);
        assertThat(discord.sent).isEmpty();
    }

    @Test
    void duplicateActivityIdsSendOnlyOnce() {
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.ANNOUNCED);
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.DUPLICATE);
        assertThat(discord.sent).hasSize(1);
    }

    @Test
    void transientFailureIsDurablyScheduledWithBoundedBackoff() {
        strava.failure = new StravaActivityClient.ActivityUnavailableException("unavailable");

        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.RETRY_SCHEDULED);
        assertThat(deliveries.retryAt).isEqualTo(CONNECTED.plusSeconds(1));
    }

    @Test
    void departedMemberDeletesTheirConnection() {
        discord.failure = new DiscordDeliveryException(DiscordDeliveryException.Kind.MEMBER_GONE, "gone", null);

        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.MEMBER_GONE);
        assertThat(connections.deletedMember).isEqualTo("member");
    }

    @Test
    void inaccessibleChannelDisablesFutureAnnouncements() {
        FakeConfiguration configuration = new FakeConfiguration();
        AnnounceStravaActivity channelService = new AnnounceStravaActivity(connections, configuration, deliveries,
                strava, new FakeDiscord(new DiscordDeliveryException(DiscordDeliveryException.Kind.CHANNEL_UNAVAILABLE, "no access", null)), tokens,
                tokenRefresher, Clock.fixed(CONNECTED, ZoneOffset.UTC));

        assertThat(channelService.announce(event())).isEqualTo(AnnounceStravaActivity.Result.CHANNEL_UNAVAILABLE);
        assertThat(configuration.get().enabled()).isFalse();
    }

    @Test
    void sentMessageWithPersistenceFailureBecomesTerminalUncertainInsteadOfRetrying() {
        deliveries.markSentFailure = true;

        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.UNCERTAIN_SENT);
        assertThat(deliveries.uncertain).contains(44L);
        assertThat(deliveries.retryAt).isNull();
    }

    @Test
    void retryWindowExhaustionIsTerminalAndDoesNotScheduleAnotherAttempt() {
        strava.failure = new StravaActivityClient.ActivityUnavailableException("unavailable");
        deliveries.seed(44, 3, CONNECTED);
        AnnounceStravaActivity expiredService = new AnnounceStravaActivity(connections, new FakeConfiguration(), deliveries, strava, discord,
                tokens, tokenRefresher, Clock.fixed(CONNECTED.plusSeconds(24 * 60 * 60), ZoneOffset.UTC));

        assertThat(expiredService.deliver(44)).isEqualTo(AnnounceStravaActivity.Result.FAILED);
        assertThat(deliveries.failed).contains(44L);
        assertThat(deliveries.retryAt).isNull();
    }

    @Test
    void refreshesAndPersistsExpiredTokensBeforeFetchingActivity() {
        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.ANNOUNCED);

        assertThat(tokenRefresher.receivedRefreshToken).isEqualTo("refresh");
        assertThat(strava.receivedAccessToken).isEqualTo("new-access");
        assertThat(tokens.decrypt(connections.connection.encryptedAccessToken())).isEqualTo("new-access");
        assertThat(tokens.decrypt(connections.connection.encryptedRefreshToken())).isEqualTo("new-refresh");
    }

    @Test
    void rejectedRefreshRequiresReconnectAndDoesNotRetryForever() {
        tokenRefresher.failure = new StravaTokenRefresher.RefreshRejectedException("rejected", null);

        assertThat(service.announce(event())).isEqualTo(AnnounceStravaActivity.Result.RECONNECT_NEEDED);
        assertThat(connections.connection.state()).isEqualTo(ConnectionState.RECONNECT_NEEDED);
        assertThat(deliveries.retryAt).isNull();
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
        public Optional<StravaConnection> findById(long id) { return id == 3 ? Optional.of(connection) : Optional.empty(); }
        public void save(StravaConnection connection) { this.connection = connection; }
        public boolean rotateTokens(long id, UUID generation, String access, String refresh, Instant expiresAt) {
            connection = new StravaConnection(connection.id(), connection.discordMemberId(), connection.stravaAthleteId(), connection.generation(),
                    connection.stravaAthleteDisplayName(), connection.grantedScope(), connection.state(), connection.connectedAt(), access, refresh, expiresAt);
            return true;
        }
        private String deletedMember;
        public void markReconnectNeeded(long athlete) { connection = StravaConnection.reconnectNeeded(connection); }
        public boolean markReconnectNeeded(long id, UUID generation) { connection = StravaConnection.reconnectNeeded(connection); return true; }
        public void saveUnlinkConfirmation(String member, UUID generation, Instant expiry) { }
        public Optional<UUID> consumeUnlinkConfirmation(String member, Instant now) { return Optional.empty(); }
        public boolean deleteByMemberIdAndGeneration(String member, UUID generation) { deletedMember = member; return true; }
    }
    private static final class FakeConfiguration implements AnnouncementConfigurationRepository {
        private AnnouncementConfiguration configuration = AnnouncementConfiguration.enabled("channel");
        public AnnouncementConfiguration get() { return configuration; }
        public void save(AnnouncementConfiguration configuration) { this.configuration = configuration; }
    }
    private static final class FakeDeliveries implements ActivityDeliveryRepository {
        private final Set<Long> claimed = new HashSet<>(); private final Set<Long> sent = new HashSet<>();
        private final java.util.Map<Long, Delivery> records = new java.util.HashMap<>();
        public boolean claim(long activityId, long connectionId, Instant now) { if (!claimed.add(activityId)) return false; records.put(activityId, new Delivery(activityId, connectionId, now, 0)); return true; }
        public java.util.List<Delivery> due(Instant now, int limit) { return java.util.List.of(); }
        public Optional<Delivery> delivery(long activityId) { return Optional.ofNullable(records.get(activityId)); }
        public boolean start(long activityId, Instant now) { return records.containsKey(activityId); }
        private boolean markSentFailure;
        private final Set<Long> uncertain = new HashSet<>();
        private final Set<Long> failed = new HashSet<>();
        void seed(long activityId, long connectionId, Instant createdAt) { records.put(activityId, new Delivery(activityId, connectionId, createdAt, 0)); }
        public void markSent(long activityId, Instant now) { if (markSentFailure) throw new IllegalStateException("database unavailable"); sent.add(activityId); }
        private Instant retryAt;
        public void retry(long activityId, Instant next) { retryAt = next; }
        public void markFailed(long activityId) { failed.add(activityId); }
        public void markUncertain(long activityId) { uncertain.add(activityId); }
        public void discard(long activityId) { }
        public void purgeExpired(Instant before) { }
    }
    private static final class FakeStrava implements StravaActivityClient {
        private StravaActivity activity = new StravaActivity(44, "Morning Run", "Run", 5200, 1800, CONNECTED.plusSeconds(1),
                140.3, 178.0, 82, "Garmin Forerunner");
        private RuntimeException failure;
        private String receivedAccessToken;
        public StravaActivity fetch(long id, String token) { receivedAccessToken = token; if (failure != null) throw failure; return activity; }
    }
    private static final class FakeTokenRefresher implements StravaTokenRefresher {
        private String receivedRefreshToken;
        private RuntimeException failure;
        public RefreshedTokens refresh(String refreshToken) {
            receivedRefreshToken = refreshToken;
            if (failure != null) throw failure;
            return new RefreshedTokens("new-access", "new-refresh", CONNECTED.plusSeconds(21_600));
        }
    }
    private static final class FakeDiscord implements DiscordActivityAnnouncements {
        private final java.util.List<DiscordActivityAnnouncement> sent = new java.util.ArrayList<>();
        private RuntimeException failure;
        FakeDiscord() { }
        FakeDiscord(RuntimeException failure) { this.failure = failure; }
        public void send(DiscordActivityAnnouncement announcement) { if (failure != null) throw failure; sent.add(announcement); }
    }
}
