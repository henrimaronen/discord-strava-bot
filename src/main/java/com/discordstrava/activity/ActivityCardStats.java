package com.discordstrava.activity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Announcement stats for the card. A null field is left off the card, never shown as a placeholder. */
public final class ActivityCardStats {
    private static final Set<String> PACE_SPORTS = Set.of("Run", "TrailRun", "VirtualRun", "Walk", "Hike");

    private ActivityCardStats() { }

    public record CardField(String name, String value) { }

    public static List<CardField> fields(DiscordActivityAnnouncement a) {
        List<CardField> fields = new ArrayList<>();
        fields.add(new CardField("Sport", sportLabel(a.sportType())));
        fields.add(distance(a.kilometres()));
        fields.add(movement(a.sportType(), a.kilometres(), a.movingTimeSeconds()));
        fields.add(new CardField("Moving time", duration(a.movingTimeSeconds())));
        fields.add(elevation(a.totalElevationGainMetres()));
        fields.add(heartRate(a.averageHeartrate(), a.maxHeartrate()));
        fields.add(sufferScore(a.sufferScore()));
        fields.removeIf(Objects::isNull);
        return List.copyOf(fields);
    }

    public static CardField distance(double kilometres) {
        if (kilometres <= 0) return null;
        return new CardField("Distance", String.format(Locale.ROOT, "%.2f km", kilometres));
    }

    public static CardField movement(String sportType, double kilometres, long movingSeconds) {
        if (kilometres <= 0 || movingSeconds <= 0) return null;
        if (PACE_SPORTS.contains(sportType)) {
            long secondsPerKilometre = Math.round(movingSeconds / kilometres);
            return new CardField("Pace", "%d:%02d /km".formatted(secondsPerKilometre / 60, secondsPerKilometre % 60));
        }
        return new CardField("Speed", String.format(Locale.ROOT, "%.1f km/h", kilometres / (movingSeconds / 3600.0)));
    }

    public static CardField elevation(Double metres) {
        if (metres == null || !(metres > 0)) return null;
        double tenths = Math.round(metres * 10) / 10.0;
        String value = tenths == Math.rint(tenths) ? "%d m".formatted((long) tenths) : String.format(Locale.ROOT, "%.1f m", tenths);
        return new CardField("Elevation", value);
    }

    public static CardField heartRate(Double average, Double max) {
        if (average == null && max == null) return null;
        if (average != null && max != null) return new CardField("Heart rate", String.format(Locale.ROOT, "%.0f / %.0f bpm", average, max));
        return new CardField("Heart rate", String.format(Locale.ROOT, "%.0f bpm", average != null ? average : max));
    }

    public static CardField sufferScore(Integer score) {
        return score == null ? null : new CardField("Suffer score", Integer.toString(score));
    }

    private static String duration(long seconds) { Duration d = Duration.ofSeconds(seconds); return "%dh %02dm %02ds".formatted(d.toHours(), d.toMinutesPart(), d.toSecondsPart()); }

    private static String sportLabel(String sportType) {
        String name = "HighIntensityIntervalTraining".equals(sportType) ? "HIIT"
                : sportType.replaceAll("(?<=[a-z])(?=[A-Z])", " ").replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
        String emoji = sportEmoji(sportType);
        return emoji.isEmpty() ? name : emoji + " " + name;
    }

    private static String sportEmoji(String sportType) {
        return switch (sportType) {
            case "Run", "TrailRun", "VirtualRun" -> "🏃";
            case "Ride", "GravelRide", "MountainBikeRide", "EBikeRide", "EMountainBikeRide", "VirtualRide" -> "🚴";
            case "Swim", "Kayaking", "Canoeing", "Rowing", "VirtualRow", "StandUpPaddling", "Surfing" -> "🏊";
            case "Walk" -> "🚶";
            case "Hike" -> "🥾";
            case "WeightTraining", "Workout", "Crossfit", "HighIntensityIntervalTraining" -> "💥";
            case "Yoga", "Pilates" -> "🧘";
            case "AlpineSki", "BackcountrySki", "NordicSki", "Snowboard", "Snowshoe" -> "⛷️";
            default -> "";
        };
    }
}
