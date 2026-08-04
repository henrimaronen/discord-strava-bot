package com.discordstrava.activity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcActivityDeliveryRepository implements ActivityDeliveryRepository {
    private final JdbcTemplate jdbc;
    public JdbcActivityDeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean claim(long activityId, long connectionId) {
        try {
            return jdbc.update("insert into activity_delivery (activity_id, connection_id, state) values (?, ?, 'PROCESSING')",
                    activityId, connectionId) == 1;
        } catch (DuplicateKeyException duplicate) { return false; }
    }
    @Override public void markSent(long activityId) {
        jdbc.update("update activity_delivery set state = 'SENT', sent_at = current_timestamp where activity_id = ?", activityId);
    }
}
