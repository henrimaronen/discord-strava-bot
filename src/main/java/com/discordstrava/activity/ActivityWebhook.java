package com.discordstrava.activity;

/** Minimal validated subset of a Strava webhook event. */
public record ActivityWebhook(String objectType, String aspectType, long activityId, long athleteId,
        long subscriptionId) {
    public boolean isActivityCreate() {
        return "activity".equals(objectType) && "create".equals(aspectType)
                && activityId > 0 && athleteId > 0 && subscriptionId > 0;
    }
}
