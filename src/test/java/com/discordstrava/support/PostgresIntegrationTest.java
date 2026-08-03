package com.discordstrava.support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Base for database integration tests; it never reaches Discord or Strava. */
@Testcontainers
public abstract class PostgresIntegrationTest {
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
