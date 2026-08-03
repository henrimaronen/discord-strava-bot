package com.discordstrava.runtime;

import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.configuration.ConfigureAnnouncementChannel;
import com.discordstrava.configuration.JdbcAnnouncementConfigurationRepository;
import com.discordstrava.discord.DiscordConfigurationListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @ConditionalOnMissingBean(JdbcTemplate.class)
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    AnnouncementConfigurationRepository announcementConfigurationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAnnouncementConfigurationRepository(jdbcTemplate);
    }

    @Bean
    ConfigureAnnouncementChannel configureAnnouncementChannel(
            RuntimeSettings settings, AnnouncementConfigurationRepository repository) {
        return new ConfigureAnnouncementChannel(settings.discordGuildId(), repository, channelId -> false);
    }

    @Bean
    DiscordConfigurationListener discordConfigurationListener(
            RuntimeSettings settings, ConfigureAnnouncementChannel configuration) {
        return new DiscordConfigurationListener(settings.discordGuildId(), configuration);
    }

    @Bean
    @ConditionalOnMissingBean(DiscordClientFactory.class)
    DiscordClientFactory discordClientFactory() {
        return (token, eventListener) -> JDABuilder.createLight(token).addEventListeners(eventListener).build();
    }

    @Bean(destroyMethod = "shutdown")
    @DependsOn("databaseMigration")
    JDA discordClient(
            RuntimeSettings settings,
            DiscordClientFactory discordClientFactory,
            DiscordConfigurationListener configurationListener) {
        // createLight retains no user/member cache and does not enable privileged intents.
        return discordClientFactory.start(settings.discordToken(), configurationListener);
    }

}
