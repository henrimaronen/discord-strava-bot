package com.discordstrava.activity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Drives durable retries; an interrupted process resumes from PostgreSQL on its next start. */
public final class ActivityDeliveryRetryWorker implements AutoCloseable {
    private final AnnounceStravaActivity announcements;
    private final ActivityDeliveryRepository deliveries;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    public ActivityDeliveryRetryWorker(AnnounceStravaActivity announcements, ActivityDeliveryRepository deliveries,
            Clock clock, ScheduledExecutorService scheduler) {
        this.announcements = announcements;
        this.deliveries = deliveries;
        this.clock = clock;
        this.scheduler = scheduler;
        scheduler.scheduleWithFixedDelay(this::runSafely, 0, 1, TimeUnit.MINUTES);
    }

    public void runOnce() {
        Instant now = clock.instant();
        for (ActivityDeliveryRepository.Delivery delivery : deliveries.due(now, 100)) {
            announcements.deliver(delivery.activityId());
        }
        deliveries.purgeExpired(now.minus(Duration.ofDays(7)));
    }

    private void runSafely() {
        try { runOnce(); } catch (RuntimeException ignored) { /* individual delivery errors are persisted by the workflow */ }
    }

    @Override public void close() { scheduler.shutdown(); }
}
