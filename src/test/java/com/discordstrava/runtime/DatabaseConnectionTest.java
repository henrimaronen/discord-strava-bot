package com.discordstrava.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DatabaseConnectionTest {
    @Test
    void convertsRailwayPostgresUrlAndDecodesCredentials() {
        DatabaseConnection connection = DatabaseConnection.from(
                "postgresql://bot%40example.com:pa%3Ass%2Fword@db.railway.internal:5432/railway?sslmode=require");

        assertThat(connection.jdbcUrl())
                .isEqualTo("jdbc:postgresql://db.railway.internal:5432/railway?sslmode=require");
        assertThat(connection.username()).isEqualTo("bot@example.com");
        assertThat(connection.password()).isEqualTo("pa:ss/word");
    }

    @Test
    void leavesJdbcUrlsForLocalDevelopmentUntouched() {
        DatabaseConnection connection = DatabaseConnection.from("jdbc:postgresql://localhost:5432/bot");

        assertThat(connection.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/bot");
        assertThat(connection.username()).isNull();
        assertThat(connection.password()).isNull();
    }

}
