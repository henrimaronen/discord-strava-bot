package com.discordstrava.web;

import com.discordstrava.activity.ActivityWebhook;
import com.discordstrava.activity.AnnounceStravaActivity;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Fast Strava subscription validation and event acknowledgement adapter. */
@RestController @RequestMapping("/webhooks/strava")
public final class StravaWebhookController {
    private final String verifyToken;
    private final long subscriptionId;
    private final AnnounceStravaActivity announcements;
    private final Executor executor;

    public StravaWebhookController(@Value("${STRAVA_WEBHOOK_VERIFY_TOKEN}") String verifyToken,
            @Value("${STRAVA_WEBHOOK_SUBSCRIPTION_ID}") long subscriptionId,
            AnnounceStravaActivity announcements, Executor executor) {
        this.verifyToken = verifyToken;
        this.subscriptionId = subscriptionId;
        this.announcements = announcements;
        this.executor = executor;
    }
    @GetMapping public ResponseEntity<Map<String, String>> verify(@RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token, @RequestParam("hub.challenge") String challenge) {
        if (!"subscribe".equals(mode) || !verifyToken.equals(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(Map.of("hub.challenge", challenge));
    }
    @PostMapping public ResponseEntity<Void> receive(@RequestBody EventBody body) {
        ActivityWebhook event = new ActivityWebhook(body.object_type(), body.aspect_type(), body.object_id(), body.owner_id(), body.subscription_id());
        if (event.isActivityCreate() && event.subscriptionId() == subscriptionId) executor.execute(() -> announcements.announce(event));
        return ResponseEntity.ok().build();
    }
    public record EventBody(String object_type, String aspect_type, long object_id, long owner_id, long subscription_id) { }
}
