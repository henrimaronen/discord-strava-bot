package com.discordstrava.connection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;

/** Application workflow for private Strava connection and OAuth callback handling. */
public final class ConnectStrava {
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private final String discordGuildId;
    private final String clientId;
    private final String publicBaseUrl;
    private final StravaConnectionRepository repository;
    private final StravaOAuthClient strava;
    private final TokenCipher tokens;
    private final Clock clock;
    private final SecureRandom random;

    public ConnectStrava(String discordGuildId, String clientId, String publicBaseUrl,
            StravaConnectionRepository repository, StravaOAuthClient strava, TokenCipher tokens, Clock clock) {
        this(discordGuildId, clientId, publicBaseUrl, repository, strava, tokens, clock, new SecureRandom());
    }

    ConnectStrava(String discordGuildId, String clientId, String publicBaseUrl,
            StravaConnectionRepository repository, StravaOAuthClient strava, TokenCipher tokens, Clock clock,
            SecureRandom random) {
        this.discordGuildId = discordGuildId;
        this.clientId = clientId;
        this.publicBaseUrl = publicBaseUrl;
        this.repository = repository;
        this.strava = strava;
        this.tokens = tokens;
        this.clock = clock;
        this.random = random;
    }

    public StartResult start(StartRequest request) {
        if (!discordGuildId.equals(request.discordGuildId())) {
            return StartResult.wrongDiscordServer();
        }
        Instant now = clock.instant();
        String state = newState();
        repository.saveOAuthState(new OAuthState(state, request.discordMemberId(), request.discordGuildId(),
                now.plus(STATE_LIFETIME)));
        return StartResult.link(authorizationUrl(state));
    }

    public CallbackResult complete(String code, String state, String error) {
        if (state == null || state.isBlank()) {
            return CallbackResult.INVALID_OR_EXPIRED;
        }
        var pending = repository.consumeOAuthState(state, clock.instant());
        if (pending.isEmpty()) {
            return CallbackResult.INVALID_OR_EXPIRED;
        }
        if (error != null || code == null || code.isBlank()) {
            return CallbackResult.AUTHORIZATION_FAILED;
        }
        final StravaOAuthClient.Authorization authorization;
        try {
            authorization = strava.exchange(code);
        } catch (StravaOAuthClient.AuthorizationException exception) {
            return CallbackResult.AUTHORIZATION_FAILED;
        }
        String grantedScope = authorization.grantedScope();
        if (!hasActivityReadScope(grantedScope)) {
            return CallbackResult.INSUFFICIENT_SCOPE;
        }
        String memberId = pending.get().discordMemberId();
        var owner = repository.findByAthleteId(authorization.athleteId());
        if (owner.isPresent() && !owner.get().discordMemberId().equals(memberId)) {
            return CallbackResult.ATHLETE_ALREADY_LINKED;
        }
        Instant now = clock.instant();
        try {
            repository.save(StravaConnection.active(memberId, authorization.athleteId(), authorization.athleteDisplayName(), grantedScope, now,
                    tokens.encrypt(authorization.accessToken()), tokens.encrypt(authorization.refreshToken()),
                    authorization.expiresAt()));
        } catch (AthleteAlreadyLinkedException exception) {
            return CallbackResult.ATHLETE_ALREADY_LINKED;
        }
        return CallbackResult.CONNECTED;
    }

    public Status status(String discordMemberId) {
        return repository.findByMemberId(discordMemberId)
                .map(connection -> new Status(connection.state(), connection.stravaAthleteDisplayName()))
                .orElse(new Status(null, null));
    }

    public boolean requestUnlink(String discordMemberId) {
        var connection = repository.findByMemberId(discordMemberId);
        if (connection.isEmpty()) return false;
        repository.saveUnlinkConfirmation(discordMemberId, connection.get().generation(), clock.instant().plus(Duration.ofMinutes(5)));
        return true;
    }

    public boolean confirmUnlink(String discordMemberId) {
        return repository.consumeUnlinkConfirmation(discordMemberId, clock.instant())
                .map(generation -> repository.deleteByMemberIdAndGeneration(discordMemberId, generation))
                .orElse(false);
    }

    /** Used by webhook/token processing when Strava deauthorizes an Athlete or rejects a token. */
    public void authorizationFailed(long stravaAthleteId) {
        repository.markReconnectNeeded(stravaAthleteId);
    }

    private String authorizationUrl(String state) {
        return "https://www.strava.com/oauth/authorize?client_id=" + encode(clientId)
                + "&response_type=code&redirect_uri=" + encode(publicBaseUrl + "/oauth/strava/callback")
                + "&approval_prompt=force&scope=" + encode("activity:read") + "&state=" + encode(state);
    }

    private String newState() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasActivityReadScope(String scope) {
        return scope != null && Arrays.stream(scope.split("[, ]+"))
                .anyMatch("activity:read"::equals);
    }

    public record StartRequest(String discordGuildId, String discordMemberId) {
    }

    public record StartResult(Result result, String authorizationUrl) {
        static StartResult link(String authorizationUrl) { return new StartResult(Result.LINK_READY, authorizationUrl); }
        static StartResult wrongDiscordServer() { return new StartResult(Result.WRONG_DISCORD_SERVER, null); }
    }

    public enum Result { LINK_READY, WRONG_DISCORD_SERVER }

    public enum CallbackResult {
        CONNECTED, INVALID_OR_EXPIRED, AUTHORIZATION_FAILED, INSUFFICIENT_SCOPE, ATHLETE_ALREADY_LINKED
    }

    public record Status(ConnectionState state, String athleteDisplayName) {
        public boolean connected() { return state != null; }
    }
}
