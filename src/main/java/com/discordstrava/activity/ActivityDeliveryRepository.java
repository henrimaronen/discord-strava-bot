package com.discordstrava.activity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable Activity-ID idempotency boundary. Raw events and Activity data are never retained. */
public interface ActivityDeliveryRepository {
    boolean claim(long activityId, long connectionId, Instant now);
    List<Delivery> due(Instant now, int limit);
    Optional<Delivery> delivery(long activityId);
    boolean start(long activityId, Instant now);
    void markSent(long activityId, Instant now);
    void retry(long activityId, Instant nextAttemptAt);
    void markFailed(long activityId);
    void markUncertain(long activityId);
    void discard(long activityId);
    void purgeExpired(Instant before);

    record Delivery(long activityId, long connectionId, Instant createdAt, int attempts) { }
}
