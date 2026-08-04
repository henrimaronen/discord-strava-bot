package com.discordstrava.activity;

/** Durable Activity-ID idempotency boundary. Raw events and Activity data are never retained. */
public interface ActivityDeliveryRepository {
    boolean claim(long activityId, long connectionId);
    void markSent(long activityId);
}
