package com.discordstrava.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import com.discordstrava.configuration.ConfigureAnnouncementChannel;
import com.discordstrava.discord.DiscordConfigurationListener;
import javax.sql.DataSource;
import net.dv8tion.jda.api.JDA;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class RuntimeConfigurationTest {
    @Test
    void completesMigrationsBeforeStartingDiscord() {
        List<String> events = new ArrayList<>();
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenAnswer(invocation -> {
            events.add("migration");
            return null;
        });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RuntimeSettings.class, this::validSettings);
            context.registerBean(DataSource.class, () -> mock(DataSource.class));
            context.registerBean(Flyway.class, () -> flyway);
            context.registerBean(DiscordClientFactory.class, () -> (token, eventListener) -> {
                events.add("discord");
                assertThat(eventListener).isInstanceOf(DiscordConfigurationListener.class);
                return mock(JDA.class);
            });
            context.register(RuntimeConfiguration.class);
            context.refresh();

            assertThat(context.getBean(JdbcTemplate.class)).isNotNull();
            assertThat(context.getBean(ConfigureAnnouncementChannel.class)).isNotNull();
            assertThat(context.getBean(DiscordConfigurationListener.class)).isNotNull();
        }

        assertThat(events).containsExactly("migration", "discord");
    }

    private RuntimeSettings validSettings() {
        return new RuntimeSettings(
                "jdbc:postgresql://localhost:5432/bot", "discord-token", "123456789012345678",
                "12345", "strava-secret", "verify-token", "encryption-key", "https://bot.example.com");
    }
}
