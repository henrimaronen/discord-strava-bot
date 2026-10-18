package com.discordstrava.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.time.ZoneOffset;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class JdaActivityAnnouncementsTest {
    private static final String PHOTO = "https://dgtzuqphqg23d.cloudfront.net/photo-600x450.jpg";

    @Test
    void runCardListsOnlyPresentStatsAsInlineFieldsInOrder() {
        MessageEmbed embed = embed(run(null, null, null, null));

        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName, MessageEmbed.Field::getValue)
                .containsExactly(
                        tuple("Sport", "🏃 Run"),
                        tuple("Distance", "5.20 km"),
                        tuple("Pace", "5:46 /km"),
                        tuple("Moving time", "0h 30m 00s"));
        assertThat(embed.getFields()).allMatch(MessageEmbed.Field::isInline);
    }

    @Test
    void cardHeadsWithMemberAndActivityLink() {
        MessageEmbed embed = embed(run(null, null, null, null));

        assertThat(embed.getAuthor().getName()).isEqualTo("Henri");
        assertThat(embed.getTitle()).isEqualTo("Morning Run");
        assertThat(embed.getUrl()).isEqualTo("https://www.strava.com/activities/44");
        assertThat(embed.getTimestamp()).isEqualTo(Instant.parse("2026-08-04T06:30:00Z").atOffset(ZoneOffset.UTC));
    }

    @Test
    void httpsPhotoBecomesTheLargeImageAndNeverAThumbnail() {
        MessageEmbed embed = embed(run(PHOTO, null, null, null));

        assertThat(embed.getImage().getUrl()).isEqualTo(PHOTO);
        assertThat(embed.getThumbnail()).isNull();
    }

    @Test
    void cardWithoutPhotoHasNoImage() {
        assertThat(embed(run(null, null, null, null)).getImage()).isNull();
    }

    @Test
    void photoUrlDiscordWouldRejectIsDroppedAndTheCardStillBuilds() {
        MessageEmbed embed = embed(run("https://example.com/" + "a".repeat(MessageEmbed.URL_MAX_LENGTH), null, null, null));

        assertThat(embed.getImage()).isNull();
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).contains("Pace");
    }

    @Test
    void descriptionAndDeviceFooterAreOmittedWhenNull() {
        MessageEmbed embed = embed(run(null, null, null, null));

        assertThat(embed.getDescription()).isNull();
        assertThat(embed.getFooter()).isNull();
    }

    @Test
    void descriptionAndDeviceFooterAreShownWhenPresent() {
        MessageEmbed embed = embed(run(null, "Easy miles.", "Garmin Forerunner 265", null));

        assertThat(embed.getDescription()).isEqualTo("Easy miles.");
        assertThat(embed.getFooter().getText()).isEqualTo("Garmin Forerunner 265");
    }

    @Test
    void rideCardShowsSpeedAndElevation() {
        MessageEmbed embed = embed(new DiscordActivityAnnouncement("channel", "member", "Lunch Ride", "Ride", 40, 3600,
                Instant.parse("2026-08-04T06:30:00Z"), "https://www.strava.com/activities/45", null, null, null, null, null,
                128.0, null));

        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName, MessageEmbed.Field::getValue)
                .contains(tuple("Speed", "40.0 km/h"), tuple("Elevation", "128 m"));
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).doesNotContain("Pace");
    }

    private static MessageEmbed embed(DiscordActivityAnnouncement a) {
        return JdaActivityAnnouncements.embed(a, "Henri", "https://cdn.discordapp.com/embed/avatars/0.png");
    }

    private static DiscordActivityAnnouncement run(String photoUrl, String description, String deviceName, Double elevation) {
        return new DiscordActivityAnnouncement("channel", "member", "Morning Run", "Run", 5.2, 1800,
                Instant.parse("2026-08-04T06:30:00Z"), "https://www.strava.com/activities/44", null, null, null, deviceName,
                description, elevation, photoUrl);
    }
}
