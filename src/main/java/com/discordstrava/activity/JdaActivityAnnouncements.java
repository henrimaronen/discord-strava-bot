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
            channel.sendMessageEmbeds(new EmbedBuilder().setTitle(member.getEffectiveName())
                .addField("Activity", a.activityName(), false).addField("Sport", a.sportType(), true)
                .addField("Distance", String.format(Locale.ROOT, "%.2f km", a.kilometres()), true)
                .addField("Moving time", duration(a.movingTimeSeconds()), true)
                .addField("When", "<t:" + a.startDate().getEpochSecond() + ":F>", false)
                .setUrl(a.stravaUrl()).build()).complete();
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
}
