package com.discordstrava.activity;

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
                .setTimestamp(a.startDate());
            for (ActivityCardStats.CardField field : ActivityCardStats.fields(a)) embed.addField(field.name(), field.value(), true);
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
    static int sportColor(String sportType) {
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
