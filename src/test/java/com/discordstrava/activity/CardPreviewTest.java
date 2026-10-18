package com.discordstrava.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Writes one HTML facsimile of the Discord card per fixture to /tmp/card-preview/ for visual review. */
class CardPreviewTest {
    private static final Path OUTPUT = Path.of("/tmp/card-preview");
    private static final String MEMBER = "Henri";

    @Test
    void writesOnePreviewPerFixture() throws IOException {
        Files.createDirectories(OUTPUT);
        for (Map.Entry<String, DiscordActivityAnnouncement> fixture : fixtures().entrySet()) {
            Files.writeString(OUTPUT.resolve(fixture.getKey() + ".html"), html(fixture.getValue()));
        }

        assertThat(preview("morning-run")).contains(field("Distance", "5.20 km"), field("Pace", "5:46 /km"),
                field("Heart rate", "140 / 178 bpm"));
        assertThat(preview("ride")).contains(field("Speed", "40.0 km/h")).doesNotContain(">Pace<");
        assertThat(preview("hiit")).contains(">Moving time<").doesNotContain(">Distance<", ">Pace<", ">Speed<");
        assertThat(preview("elevation-128")).contains(field("Elevation", "128 m"));
        assertThat(preview("elevation-0")).doesNotContain(">Elevation<");
        assertThat(preview("zero-moving-seconds")).contains(">Distance<").doesNotContain(">Pace<", ">Speed<");
        assertThat(preview("walk")).contains(field("Pace", "5:46 /km"));
        assertThat(preview("no-heart-rate")).contains("Easy miles.")
                .doesNotContain(">Heart rate<", ">Suffer score<", "—");
        assertThat(preview("ebike-ride")).contains("E Bike Ride", ">Speed<");
        assertThat(preview("elevation-12-5")).contains(field("Elevation", "12.5 m"));
    }

    private static Map<String, DiscordActivityAnnouncement> fixtures() {
        Map<String, DiscordActivityAnnouncement> fixtures = new LinkedHashMap<>();
        fixtures.put("morning-run", card("Morning Run", "Run", 5.2, 1800, null, 140.3, 178.0, 82, "Easy miles."));
        fixtures.put("ride", card("Lunch Ride", "Ride", 40, 3600, 310.0, 128.0, 165.0, 61, null));
        fixtures.put("hiit", card("Evening HIIT", "HighIntensityIntervalTraining", 0, 1800, null, 150.0, 182.0, 40, null));
        fixtures.put("elevation-128", card("Hill Run", "Run", 5.2, 1800, 128.0, 140.3, 178.0, 82, null));
        fixtures.put("elevation-0", card("Track Run", "Run", 5.2, 1800, 0.0, 140.3, 178.0, 82, null));
        fixtures.put("zero-moving-seconds", card("Manual Run", "Run", 5.2, 0, null, null, null, null, null));
        fixtures.put("walk", card("Morning Walk", "Walk", 5.2, 1800, null, 101.0, 120.0, 8, null));
        fixtures.put("no-heart-rate", card("Morning Run", "Run", 5.2, 1800, null, null, null, null, "Easy miles."));
        fixtures.put("ebike-ride", card("Commute", "EBikeRide", 12.4, 1800, 45.0, null, null, null, null));
        fixtures.put("elevation-12-5", card("Flat Run", "Run", 5.2, 1800, 12.5, 140.3, 178.0, 82, null));
        return fixtures;
    }

    private static DiscordActivityAnnouncement card(String name, String sport, double kilometres, long seconds, Double elevation,
            Double averageHeartrate, Double maxHeartrate, Integer sufferScore, String description) {
        return new DiscordActivityAnnouncement("channel", "member", name, sport, kilometres, seconds,
                Instant.parse("2026-08-04T06:30:00Z"), "https://www.strava.com/activities/44", averageHeartrate, maxHeartrate,
                sufferScore, "Garmin Forerunner 265", description, elevation);
    }

    private static String html(DiscordActivityAnnouncement a) {
        StringBuilder fields = new StringBuilder();
        for (ActivityCardStats.CardField field : ActivityCardStats.fields(a)) fields.append(field(field.name(), field.value()));
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>%s</title><style>
                body { background: #313338; color: #dbdee1; font-family: "gg sans", "Helvetica Neue", Arial, sans-serif; padding: 24px; }
                .embed { max-width: 432px; background: #2b2d31; border-left: 4px solid #%06X; border-radius: 4px; padding: 8px 16px 16px 12px; }
                .author { font-size: 14px; font-weight: 600; color: #f2f3f5; margin-top: 8px; }
                .title { display: block; font-size: 16px; font-weight: 600; color: #00a8fc; margin-top: 8px; text-decoration: none; }
                .description { font-size: 14px; margin-top: 8px; }
                .fields { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 8px; }
                .name { font-size: 14px; font-weight: 600; color: #f2f3f5; }
                .value { font-size: 14px; }
                .footer { font-size: 12px; margin-top: 8px; }
                </style></head><body><div class="embed">
                <div class="author">%s</div>
                <a class="title" href="%s">%s</a>
                %s<div class="fields">%s</div>
                <div class="footer">%s</div>
                </div></body></html>
                """.formatted(escape(a.activityName()), JdaActivityAnnouncements.sportColor(a.sportType()), escape(MEMBER),
                escape(a.stravaUrl()), escape(a.activityName()),
                a.description() == null ? "" : "<div class=\"description\">" + escape(a.description()) + "</div>",
                fields, escape(a.deviceName() == null ? a.startDate().toString() : a.deviceName() + " • " + a.startDate()));
    }

    private static String field(String name, String value) {
        return "<div class=\"field\"><div class=\"name\">" + escape(name) + "</div><div class=\"value\">" + escape(value) + "</div></div>";
    }

    private static String preview(String slug) throws IOException { return Files.readString(OUTPUT.resolve(slug + ".html")); }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
