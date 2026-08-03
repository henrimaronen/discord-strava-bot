package com.discordstrava.web;

import com.discordstrava.connection.ConnectStrava;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public browser completion endpoint; it intentionally exposes no account or token data. */
@RestController
public final class StravaOAuthController {
    private final ConnectStrava connectStrava;

    public StravaOAuthController(ConnectStrava connectStrava) {
        this.connectStrava = connectStrava;
    }

    @GetMapping(value = "/oauth/strava/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        ConnectStrava.CallbackResult result = connectStrava.complete(code, state, error);
        return switch (result) {
            case CONNECTED -> "<p>Strava connected. Return to Discord and use /status to confirm.</p>";
            case ATHLETE_ALREADY_LINKED -> "<p>Unable to connect this Strava account. Return to Discord.</p>";
            case INVALID_OR_EXPIRED, AUTHORIZATION_FAILED, INSUFFICIENT_SCOPE ->
                    "<p>Strava connection was not completed. Return to Discord and use /connect again.</p>";
        };
    }
}
