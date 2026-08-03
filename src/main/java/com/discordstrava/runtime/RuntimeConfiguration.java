package com.discordstrava.runtime;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class RuntimeConfiguration {
    @Bean
    RuntimeSettings runtimeSettings(Environment environment) {
        return RuntimeSettingsLoader.load(environment);
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    DataSource dataSource(RuntimeSettings settings) {
        DatabaseConnection connection = DatabaseConnection.from(settings.databaseUrl());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connection.jdbcUrl());
        if (connection.username() != null) {
            config.setUsername(connection.username());
        }
        if (connection.password() != null) {
            config.setPassword(connection.password());
        }
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnMissingBean(Flyway.class)
    Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).load();
    }

    @Bean("databaseMigration")
    DatabaseMigration databaseMigration(Flyway flyway) {
        flyway.migrate();
        return new DatabaseMigration();
    }

    @Bean
    @ConditionalOnMissingBean(DiscordClientFactory.class)
    DiscordClientFactory discordClientFactory() {
        return token -> JDABuilder.createLight(token).build();
    }

    @Bean(destroyMethod = "shutdown")
    @DependsOn("databaseMigration")
    JDA discordClient(RuntimeSettings settings, DiscordClientFactory discordClientFactory) {
        // createLight retains no user/member cache and does not enable privileged intents.
        return discordClientFactory.start(settings.discordToken());
    }

}
