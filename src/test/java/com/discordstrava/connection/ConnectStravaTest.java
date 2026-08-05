package com.discordstrava.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectStravaTest {
    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");
    private final FakeRepository repository = new FakeRepository();
    private final FakeStrava strava = new FakeStrava();
    private final TokenCipher cipher = new TokenCipher("test encryption secret");
    private final ConnectStrava service = new ConnectStrava("123456789012345678", "12345", "https://bot.example.com",
            repository, strava, cipher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startsPrivateFlowWithHighEntropyStateAndMinimalScope() {
        var result = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));

        assertThat(result.result()).isEqualTo(ConnectStrava.Result.LINK_READY);
        assertThat(result.authorizationUrl()).contains("scope=activity%3Aread", "redirect_uri=https%3A%2F%2Fbot.example.com%2Foauth%2Fstrava%2Fcallback");
        assertThat(repository.states).hasSize(1);
        OAuthState state = repository.states.values().iterator().next();
        assertThat(state.value()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(state.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void newFlowInvalidatesPriorPendingStateForSameMember() {
        var first = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));
        var second = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));

        assertThat(repository.states).hasSize(1);
        assertThat(service.complete("code", stateFrom(first.authorizationUrl()), null))
                .isEqualTo(ConnectStrava.CallbackResult.INVALID_OR_EXPIRED);
        assertThat(service.complete("code", stateFrom(second.authorizationUrl()), null))
                .isEqualTo(ConnectStrava.CallbackResult.CONNECTED);
    }

    @Test
    void expiredAnd_replayedStatesCannotLinkAnAthlete() {
        repository.saveOAuthState(new OAuthState("expired", "222222222222222222", "123456789012345678", NOW));

        assertThat(service.complete("code", "expired", null)).isEqualTo(ConnectStrava.CallbackResult.INVALID_OR_EXPIRED);
        var started = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));
        String state = stateFrom(started.authorizationUrl());
        assertThat(service.complete("code", state, null)).isEqualTo(ConnectStrava.CallbackResult.CONNECTED);
        assertThat(service.complete("code", state, null)).isEqualTo(ConnectStrava.CallbackResult.INVALID_OR_EXPIRED);
    }

    @Test
    void successfulReconnectReplacesFormerConnectionOnlyAfterAuthorizationSucceeds() {
        repository.save(active("222222222222222222", 1L));
        var start = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));
        strava.failure = true;

        assertThat(service.complete("code", stateFrom(start.authorizationUrl()), null))
                .isEqualTo(ConnectStrava.CallbackResult.AUTHORIZATION_FAILED);
        assertThat(repository.findByMemberId("222222222222222222").orElseThrow().stravaAthleteId()).isEqualTo(1L);

        var success = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));
        strava.failure = false;
        strava.authorization = authorization(2L, "activity:read");
        assertThat(service.complete("code", stateFrom(success.authorizationUrl()), null))
                .isEqualTo(ConnectStrava.CallbackResult.CONNECTED);
        assertThat(repository.findByMemberId("222222222222222222").orElseThrow().stravaAthleteId()).isEqualTo(2L);
    }

    @Test
    void refusesAnAlreadyLinkedAthleteWithoutRevealingOwnerOrChangingConnection() {
        repository.save(active("333333333333333333", 99L));
        strava.authorization = authorization(99L, "activity:read");
        var start = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));

        assertThat(service.complete("code", stateFrom(start.authorizationUrl()), null))
                .isEqualTo(ConnectStrava.CallbackResult.ATHLETE_ALREADY_LINKED);
        assertThat(repository.findByMemberId("222222222222222222")).isEmpty();
    }

    @Test
    void rejectedScopeDoesNotPersistTokens() {
        strava.authorization = authorization(2L, "read");
        var start = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));

        assertThat(service.complete("code", stateFrom(start.authorizationUrl()), null))
                .isEqualTo(ConnectStrava.CallbackResult.INSUFFICIENT_SCOPE);
        assertThat(repository.connections).isEmpty();
    }

    @Test
    void encryptsTokensBeforePersistenceAndCanMarkReconnectNeededWithoutTokens() {
        var start = service.start(new ConnectStrava.StartRequest("123456789012345678", "222222222222222222"));
        service.complete("code", stateFrom(start.authorizationUrl()), null);
        StravaConnection saved = repository.findByMemberId("222222222222222222").orElseThrow();

        assertThat(saved.encryptedAccessToken()).doesNotContain("access-token");
        assertThat(cipher.decrypt(saved.encryptedAccessToken())).isEqualTo("access-token");
        service.authorizationFailed(saved.stravaAthleteId());
        StravaConnection disabled = repository.findByMemberId("222222222222222222").orElseThrow();
        assertThat(disabled.state()).isEqualTo(ConnectionState.RECONNECT_NEEDED);
        assertThat(disabled.encryptedAccessToken()).isNull();
        assertThat(disabled.encryptedRefreshToken()).isNull();
    }

    @Test
    void unlinkRequiresAValidFiveMinuteConfirmationAndDeletesTokensWithConnection() {
        repository.save(active("222222222222222222", 1L));
        assertThat(service.requestUnlink("222222222222222222")).isTrue();
        assertThat(service.confirmUnlink("222222222222222222")).isTrue();
        assertThat(repository.findByMemberId("222222222222222222")).isEmpty();
        assertThat(service.confirmUnlink("222222222222222222")).isFalse();
    }

    @Test
    void expiredUnlinkConfirmationDoesNotChangeConnection() {
        repository.save(active("222222222222222222", 1L));
        repository.saveUnlinkConfirmation("222222222222222222", java.util.UUID.randomUUID(), NOW);

        assertThat(service.confirmUnlink("222222222222222222")).isFalse();
        assertThat(repository.findByMemberId("222222222222222222")).isPresent();
    }

    @Test
    void unlinkConfirmationCannotDeleteAConnectionReplacedAfterConfirmationWasRequested() {
        repository.save(active("222222222222222222", 1L));
        assertThat(service.requestUnlink("222222222222222222")).isTrue();
        repository.save(active("222222222222222222", 2L));

        assertThat(service.confirmUnlink("222222222222222222")).isFalse();
        assertThat(repository.findByMemberId("222222222222222222").orElseThrow().stravaAthleteId()).isEqualTo(2L);
    }

    private static String stateFrom(String url) {
        return url.substring(url.indexOf("state=") + 6);
    }

    private static StravaConnection active(String member, long athlete) {
        return StravaConnection.active(member, athlete, "Athlete", "activity:read", NOW, "encrypted-a", "encrypted-r", NOW.plusSeconds(3600));
    }

    private static StravaOAuthClient.Authorization authorization(long athlete, String scope) {
        return new StravaOAuthClient.Authorization(athlete, "Athlete", scope, "access-token", "refresh-token", NOW.plusSeconds(3600));
    }

    private static final class FakeStrava implements StravaOAuthClient {
        private Authorization authorization = authorization(2L, "activity:read");
        private boolean failure;

        @Override
        public Authorization exchange(String code) throws AuthorizationException {
            if (failure) throw new AuthorizationException("rejected");
            return authorization;
        }
    }

    private static final class FakeRepository implements StravaConnectionRepository {
        public boolean rotateTokens(long id, java.util.UUID generation, String access, String refresh, Instant expiresAt) { return false; }
        public boolean markReconnectNeeded(long id, java.util.UUID generation) { return false; }
        private final Map<String, OAuthState> states = new HashMap<>();
        private final Map<String, StravaConnection> connections = new HashMap<>();

        @Override public void saveOAuthState(OAuthState state) {
            states.values().removeIf(existing -> existing.discordMemberId().equals(state.discordMemberId()));
            states.put(state.value(), state);
        }
        @Override public Optional<OAuthState> consumeOAuthState(String value, Instant now) {
            OAuthState state = states.get(value);
            if (state == null || state.isExpired(now)) {
                states.remove(value);
                return Optional.empty();
            }
            states.remove(value);
            return Optional.of(state);
        }
        @Override public Optional<StravaConnection> findByMemberId(String member) { return Optional.ofNullable(connections.get(member)); }
        @Override public Optional<StravaConnection> findByAthleteId(long athlete) {
            return connections.values().stream().filter(connection -> connection.stravaAthleteId() == athlete).findFirst();
        }
        @Override public Optional<StravaConnection> findById(long id) {
            return connections.values().stream().filter(connection -> connection.id() == id).findFirst();
        }
        @Override public void save(StravaConnection connection) { connections.put(connection.discordMemberId(), connection); }
        @Override public void markReconnectNeeded(long athlete) {
            findByAthleteId(athlete).ifPresent(connection -> connections.put(connection.discordMemberId(), StravaConnection.reconnectNeeded(connection)));
        }
        @Override public void saveUnlinkConfirmation(String member, java.util.UUID connectionGeneration, Instant expiresAt) { states.put("unlink:" + member, new OAuthState("unlink:" + member, member, connectionGeneration.toString(), expiresAt)); }
        @Override public Optional<java.util.UUID> consumeUnlinkConfirmation(String member, Instant now) {
            OAuthState confirmation = states.remove("unlink:" + member);
            return confirmation != null && !confirmation.isExpired(now) ? Optional.of(java.util.UUID.fromString(confirmation.discordGuildId())) : Optional.empty();
        }
        @Override public boolean deleteByMemberIdAndGeneration(String member, java.util.UUID generation) {
            return findByMemberId(member).filter(connection -> connection.generation().equals(generation))
                    .map(connection -> connections.remove(member) != null).orElse(false);
        }
    }
}
