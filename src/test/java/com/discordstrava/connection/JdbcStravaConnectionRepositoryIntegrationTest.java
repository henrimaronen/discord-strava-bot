package com.discordstrava.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.discordstrava.support.PostgresIntegrationTest;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcStravaConnectionRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Test
    void persistsInstantTimestampsAsPostgresTimestamptz() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var repository = new JdbcStravaConnectionRepository(new JdbcTemplate(dataSource));
        Instant now = Instant.parse("2026-08-04T18:00:00Z");

        repository.saveOAuthState(new OAuthState("state", "123456789012345678", "987654321098765432", now.plusSeconds(600)));

        assertThat(repository.consumeOAuthState("state", now)).isPresent();
    }
}
