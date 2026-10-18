package com.discordstrava.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.discordstrava.activity.ActivityCardStats.CardField;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ActivityCardStatsTest {
    @Test
    void runShowsDistanceAndPaceWithASpaceBeforeTheUnit() {
        assertThat(ActivityCardStats.distance(5.2)).isEqualTo(new CardField("Distance", "5.20 km"));
        assertThat(ActivityCardStats.movement("Run", 5.2, 1800)).isEqualTo(new CardField("Pace", "5:46 /km"));
    }

    @Test
    void everyFootSportShowsPace() {
        for (String sport : new String[] {"Run", "TrailRun", "VirtualRun", "Walk", "Hike"}) {
            assertThat(ActivityCardStats.movement(sport, 5.2, 1800)).isEqualTo(new CardField("Pace", "5:46 /km"));
        }
    }

    @Test
    void paceRoundsSecondsAndCarriesIntoTheMinute() {
        assertThat(ActivityCardStats.movement("Run", 10, 3595)).isEqualTo(new CardField("Pace", "6:00 /km"));
        assertThat(ActivityCardStats.movement("Run", 10, 3594)).isEqualTo(new CardField("Pace", "5:59 /km"));
    }

    @Test
    void rideShowsSpeedAndNoPace() {
        assertThat(ActivityCardStats.movement("Ride", 40, 3600)).isEqualTo(new CardField("Speed", "40.0 km/h"));
        assertThat(ActivityCardStats.movement("EBikeRide", 25.5, 3600)).isEqualTo(new CardField("Speed", "25.5 km/h"));
        assertThat(ActivityCardStats.movement("Swim", 2, 3600)).isEqualTo(new CardField("Speed", "2.0 km/h"));
    }

    @Test
    void sessionThatDidNotMoveHasNoDistancePaceOrSpeed() {
        assertThat(ActivityCardStats.distance(0)).isNull();
        assertThat(ActivityCardStats.movement("HighIntensityIntervalTraining", 0, 1800)).isNull();
        assertThat(ActivityCardStats.movement("Run", 0, 1800)).isNull();
        assertThat(ActivityCardStats.distance(0.004)).isNull();
        assertThat(ActivityCardStats.movement("Run", 0.004, 1800)).isNull();
    }

    @Test
    void zeroMovingSecondsKeepsDistanceButDropsPaceAndSpeed() {
        assertThat(ActivityCardStats.distance(5.2)).isNotNull();
        assertThat(ActivityCardStats.movement("Run", 5.2, 0)).isNull();
        assertThat(ActivityCardStats.movement("Ride", 5.2, 0)).isNull();
    }

    @Test
    void elevationShowsWholeMetresOrOneDecimalAndIsAbsentWithoutGain() {
        assertThat(ActivityCardStats.elevation(128.0)).isEqualTo(new CardField("Elevation", "128 m"));
        assertThat(ActivityCardStats.elevation(12.5)).isEqualTo(new CardField("Elevation", "12.5 m"));
        assertThat(ActivityCardStats.elevation(12.04)).isEqualTo(new CardField("Elevation", "12 m"));
        assertThat(ActivityCardStats.elevation(0.0)).isNull();
        assertThat(ActivityCardStats.elevation(0.04)).isNull();
        assertThat(ActivityCardStats.elevation(null)).isNull();
    }

    @Test
    void heartRateShowsWhicheverNumbersExist() {
        assertThat(ActivityCardStats.heartRate(140.3, 178.0)).isEqualTo(new CardField("Heart rate", "140 / 178 bpm"));
        assertThat(ActivityCardStats.heartRate(140.3, null)).isEqualTo(new CardField("Heart rate", "140 bpm"));
        assertThat(ActivityCardStats.heartRate(null, 178.0)).isEqualTo(new CardField("Heart rate", "178 bpm"));
        assertThat(ActivityCardStats.heartRate(null, null)).isNull();
    }

    @Test
    void sufferScoreOfZeroIsPresentAndNullIsAbsent() {
        assertThat(ActivityCardStats.sufferScore(0)).isEqualTo(new CardField("Suffer score", "0"));
        assertThat(ActivityCardStats.sufferScore(82)).isEqualTo(new CardField("Suffer score", "82"));
        assertThat(ActivityCardStats.sufferScore(null)).isNull();
    }

    @Test
    void cardListsOnlyPresentFieldsInOrder() {
        assertThat(ActivityCardStats.fields(announcement("Run", 5.2, 1800, 128.0, 140.3, 178.0, 82))).containsExactly(
                new CardField("Sport", "🏃 Run"),
                new CardField("Distance", "5.20 km"),
                new CardField("Pace", "5:46 /km"),
                new CardField("Moving time", "0h 30m 00s"),
                new CardField("Elevation", "128 m"),
                new CardField("Heart rate", "140 / 178 bpm"),
                new CardField("Suffer score", "82"));
        assertThat(ActivityCardStats.fields(announcement("HighIntensityIntervalTraining", 0, 1800, null, null, null, null)))
                .containsExactly(new CardField("Sport", "💥 HIIT"), new CardField("Moving time", "0h 30m 00s"));
    }

    @Test
    void sportLabelSplitsCamelCase() {
        assertThat(ActivityCardStats.fields(announcement("EBikeRide", 20, 3600, null, null, null, null)))
                .first().isEqualTo(new CardField("Sport", "🚴 E Bike Ride"));
    }

    private static DiscordActivityAnnouncement announcement(String sport, double kilometres, long seconds, Double elevation,
            Double averageHeartrate, Double maxHeartrate, Integer sufferScore) {
        return new DiscordActivityAnnouncement("channel", "member", "Morning Run", sport, kilometres, seconds,
                Instant.parse("2026-08-04T10:00:01Z"), "https://www.strava.com/activities/44", averageHeartrate, maxHeartrate,
                sufferScore, null, null, elevation, null);
    }
}
