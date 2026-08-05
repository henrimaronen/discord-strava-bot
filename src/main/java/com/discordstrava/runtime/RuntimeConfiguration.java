package com.discordstrava.runtime;

import com.discordstrava.configuration.AnnouncementConfigurationRepository;
import com.discordstrava.configuration.ConfigureAnnouncementChannel;
import com.discordstrava.configuration.JdbcAnnouncementConfigurationRepository;
import com.discordstrava.connection.ConnectStrava;
import com.discordstrava.connection.HttpStravaOAuthClient;
import com.discordstrava.connection.HttpStravaTokenRefresher;
import com.discordstrava.connection.JdbcStravaConnectionRepository;
import com.discordstrava.connection.StravaConnectionRepository;
import com.discordstrava.connection.StravaOAuthClient;
import com.discordstrava.connection.StravaTokenRefresher;
import com.discordstrava.connection.TokenCipher;
import com.discordstrava.activity.ActivityDeliveryRepository;
import com.discordstrava.activity.ActivityDeliveryRetryWorker;
import com.discordstrava.activity.AnnounceStravaActivity;
import com.discordstrava.activity.DiscordActivityAnnouncements;
import com.discordstrava.activity.HttpStravaActivityClient;
import com.discordstrava.activity.JdaActivityAnnouncements;
import com.discordstrava.activity.JdbcActivityDeliveryRepository;
import com.discordstrava.activity.StravaActivityClient;
import com.discordstrava.discord.DiscordConfigurationListener;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class RuntimeConfiguration {
    private static final long DATABASE_CONNECTION_TIMEOUT_MILLIS = 2_000;
    private static final int DATABASE_QUERY_TIMEOUT_SECONDS = 5;
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
        config.setConnectionTimeout(DATABASE_CONNECTION_TIMEOUT_MILLIS);
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
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(DATABASE_QUERY_TIMEOUT_SECONDS);
        return jdbcTemplate;
    }

    @Bean
    AnnouncementConfigurationRepository announcementConfigurationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAnnouncementConfigurationRepository(jdbcTemplate);
    }

    @Bean
    StravaConnectionRepository stravaConnectionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcStravaConnectionRepository(jdbcTemplate);
    }

    @Bean
    TokenCipher tokenCipher(RuntimeSettings settings) {
        return new TokenCipher(settings.oauthTokenEncryptionKey());
    }

    @Bean
    @ConditionalOnMissingBean(StravaOAuthClient.class)
    StravaOAuthClient stravaOAuthClient(RuntimeSettings settings) {
        return new HttpStravaOAuthClient(settings.stravaClientId(), settings.stravaClientSecret());
    }

    @Bean
    @ConditionalOnMissingBean(StravaTokenRefresher.class)
    StravaTokenRefresher stravaTokenRefresher(RuntimeSettings settings) {
        return new HttpStravaTokenRefresher(settings.stravaClientId(), settings.stravaClientSecret());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ConnectStrava connectStrava(RuntimeSettings settings, StravaConnectionRepository repository,
            StravaOAuthClient stravaOAuthClient, TokenCipher tokenCipher, Clock clock) {
        return new ConnectStrava(settings.discordGuildId(), settings.stravaClientId(), settings.publicBaseUrl(),
                repository, stravaOAuthClient, tokenCipher, clock);
    }

    @Bean
    ActivityDeliveryRepository activityDeliveryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcActivityDeliveryRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(StravaActivityClient.class)
    StravaActivityClient stravaActivityClient() {
        return new HttpStravaActivityClient();
    }

    @Bean
    DiscordActivityAnnouncements discordActivityAnnouncements(JDA jda, RuntimeSettings settings) {
        return new JdaActivityAnnouncements(jda, settings.discordGuildId());
    }

    @Bean
    AnnounceStravaActivity announceStravaActivity(StravaConnectionRepository connections,
            AnnouncementConfigurationRepository configuration, ActivityDeliveryRepository deliveries,
            StravaActivityClient strava, DiscordActivityAnnouncements discord, TokenCipher tokenCipher,
            StravaTokenRefresher tokenRefresher, Clock clock) {
        return new AnnounceStravaActivity(connections, configuration, deliveries, strava, discord, tokenCipher, tokenRefresher, clock);
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService activityDeliveryScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "activity-delivery-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "close")
    ActivityDeliveryRetryWorker activityDeliveryRetryWorker(AnnounceStravaActivity announcements,
            ActivityDeliveryRepository deliveries, Clock clock, ScheduledExecutorService activityDeliveryScheduler) {
        return new ActivityDeliveryRetryWorker(announcements, deliveries, clock, activityDeliveryScheduler);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService webhookExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "strava-webhook");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService discordCommandExecutor() {
        return new ThreadPoolExecutor(5, 5, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(20), runnable -> {
            Thread thread = new Thread(runnable, "discord-command");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    ConfigureAnnouncementChannel configureAnnouncementChannel(
            RuntimeSettings settings, AnnouncementConfigurationRepository repository) {
        return new ConfigureAnnouncementChannel(settings.discordGuildId(), repository, channelId -> false);
    }

    @Bean
    DiscordConfigurationListener discordConfigurationListener(
            RuntimeSettings settings, ConfigureAnnouncementChannel configuration, ConnectStrava connectStrava,
            AnnouncementConfigurationRepository announcementConfiguration,
            @Qualifier("discordCommandExecutor") Executor discordCommandExecutor) {
        return new DiscordConfigurationListener(settings.discordGuildId(), configuration, connectStrava,
                announcementConfiguration, discordCommandExecutor);
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
