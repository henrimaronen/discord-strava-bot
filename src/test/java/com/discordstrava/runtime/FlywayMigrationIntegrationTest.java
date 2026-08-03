package com.discordstrava.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.discordstrava.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationIntegrationTest extends PostgresIntegrationTest {
    @Test
    void appliesProductionMigrationsToPostgres() {
        var result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        assertThat(result.migrationsExecuted).isGreaterThan(0);
    }
}
