package com.discordstrava.configuration;

import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAnnouncementConfigurationRepository implements AnnouncementConfigurationRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAnnouncementConfigurationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AnnouncementConfiguration get() {
        return jdbcTemplate.query(
                        "select announcement_channel_id, enabled from announcement_configuration where id = 1",
                        resultSet -> resultSet.next()
                                ? new AnnouncementConfiguration(resultSet.getString(1), resultSet.getBoolean(2))
                                : AnnouncementConfiguration.disabled())
                ;
    }

    @Override
    public void save(AnnouncementConfiguration configuration) {
        jdbcTemplate.update(
                """
                insert into announcement_configuration (id, announcement_channel_id, enabled)
                values (1, ?, ?)
                on conflict (id) do update
                set announcement_channel_id = excluded.announcement_channel_id,
                    enabled = excluded.enabled
                """,
                configuration.channelId(), configuration.enabled());
    }
}
