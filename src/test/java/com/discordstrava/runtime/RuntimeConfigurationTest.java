package com.discordstrava.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import net.dv8tion.jda.api.JDA;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
            context.registerBean(DiscordClientFactory.class, () -> token -> {
                events.add("discord");
                return mock(JDA.class);
            });
            context.register(RuntimeConfiguration.class);
            context.refresh();
        }

        assertThat(events).containsExactly("migration", "discord");
    }

    private RuntimeSettings validSettings() {
        return new RuntimeSettings(
                "jdbc:postgresql://localhost:5432/bot", "discord-token", "123456789012345678",
                "12345", "strava-secret", "verify-token", "encryption-key", "https://bot.example.com");
    }
}
