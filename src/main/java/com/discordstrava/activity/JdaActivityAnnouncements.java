package com.discordstrava.activity;

import java.time.Duration;
import java.util.Locale;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** Thin outbound adapter. The embed exposes only {@link DiscordActivityAnnouncement}'s fields. */
public final class JdaActivityAnnouncements implements DiscordActivityAnnouncements {
    private final JDA jda; private final String guildId;
    public JdaActivityAnnouncements(JDA jda, String guildId) { this.jda = jda; this.guildId = guildId; }
    @Override public void send(DiscordActivityAnnouncement a) {
        Member member;
        try {
            member = jda.getGuildById(guildId).retrieveMemberById(a.discordMemberId()).complete();
        } catch (ErrorResponseException failure) {
            if (failure.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                throw new DiscordDeliveryException(DiscordDeliveryException.Kind.MEMBER_GONE, "Discord member absent", failure);
            }
            throw new DiscordDeliveryException(DiscordDeliveryException.Kind.TRANSIENT, "Discord member lookup failed", failure);
        }
        GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, a.channelId());
        if (channel == null) throw new DiscordDeliveryException(DiscordDeliveryException.Kind.CHANNEL_UNAVAILABLE, "Announcement channel unavailable", null);
        try {
            EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(member.getEffectiveName(), null, member.getEffectiveAvatarUrl())
                .setTitle(a.activityName(), a.stravaUrl())
                .setColor(sportColor(a.sportType()))
                .addField("Sport", sportLabel(a.sportType()), true)
                .addField("Distance", String.format(Locale.ROOT, "%.2f km", a.kilometres()), true)
                .addField("Moving time", duration(a.movingTimeSeconds()), true)
                .addField("Heart rate", heartRate(a.averageHeartrate(), a.maxHeartrate()), true)
                .addField("Suffer score", a.sufferScore() == null ? "—" : Integer.toString(a.sufferScore()), true)
                .setTimestamp(a.startDate());
            if (a.description() != null) embed.setDescription(a.description());
            if (a.deviceName() != null) embed.setFooter(a.deviceName());
            channel.sendMessageEmbeds(embed.build()).complete();
        } catch (ErrorResponseException failure) {
            if (failure.getErrorResponse() == ErrorResponse.UNKNOWN_CHANNEL
                    || failure.getErrorResponse() == ErrorResponse.MISSING_ACCESS
                    || failure.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                throw new DiscordDeliveryException(DiscordDeliveryException.Kind.CHANNEL_UNAVAILABLE, "Announcement channel unavailable", failure);
            }
            throw new DiscordDeliveryException(DiscordDeliveryException.Kind.TRANSIENT, "Discord send failed", failure);
        }
    }
    private static String duration(long seconds) { Duration d = Duration.ofSeconds(seconds); return "%dh %02dm %02ds".formatted(d.toHours(), d.toMinutesPart(), d.toSecondsPart()); }
    private static String heartRate(Double average, Double max) {
        if (average == null && max == null) return "—";
        if (average != null && max != null) return String.format(Locale.ROOT, "%.0f / %.0f bpm", average, max);
        return String.format(Locale.ROOT, "%.0f bpm", average != null ? average : max);
    }
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
    private static int sportColor(String sportType) {
        return switch (sportType) {
            case "Ride", "GravelRide", "MountainBikeRide", "EBikeRide", "EMountainBikeRide", "VirtualRide",
                    "Handcycle", "Velomobile" -> 0x3498DB;
            case "Swim", "Kayaking", "Canoeing", "Rowing", "VirtualRow", "StandUpPaddling", "Surfing",
                    "Kitesurf", "Windsurf", "Sail" -> 0x1ABC9C;
            case "Walk", "Hike" -> 0x27AE60;
            case "AlpineSki", "BackcountrySki", "NordicSki", "Snowboard", "Snowshoe", "IceSkate",
                    "InlineSkate", "RollerSki" -> 0x5DADE2;
            case "WeightTraining", "HighIntensityIntervalTraining", "Workout", "Crossfit", "Yoga", "Pilates",
                    "Elliptical", "StairStepper" -> 0x9B59B6;
            default -> 0xFC4C02;
        };
    }
}
