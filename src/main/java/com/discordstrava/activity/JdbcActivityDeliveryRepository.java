package com.discordstrava.activity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class JdbcActivityDeliveryRepository implements ActivityDeliveryRepository {
    private final JdbcTemplate jdbc;
    public JdbcActivityDeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean claim(long activityId, long connectionId, Instant now) {
        try {
            return jdbc.update("insert into activity_delivery (activity_id, connection_id, state, created_at, next_attempt_at) values (?, ?, 'PENDING', ?, ?)",
                    activityId, connectionId, timestamp(now), timestamp(now)) == 1;
        } catch (DuplicateKeyException duplicate) { return false; }
    }
    @Override public List<Delivery> due(Instant now, int limit) {
        return jdbc.query("select activity_id, connection_id, created_at, attempts from activity_delivery where (state in ('PENDING', 'RETRY') and next_attempt_at <= ?) or (state = 'PROCESSING' and processing_started_at <= ?) order by next_attempt_at limit ?",
                (rs, row) -> new Delivery(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant(), rs.getInt(4)), timestamp(now), timestamp(now.minusSeconds(300)), limit);
    }
    @Override public Optional<Delivery> delivery(long activityId) {
        return jdbc.query("select activity_id, connection_id, created_at, attempts from activity_delivery where activity_id = ?",
                rs -> rs.next() ? Optional.of(new Delivery(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant(), rs.getInt(4))) : Optional.empty(), activityId);
    }
    @Override public boolean start(long activityId, Instant now) {
        return jdbc.update("update activity_delivery set state = 'PROCESSING', processing_started_at = ?, attempts = attempts + 1 where activity_id = ? and ((state in ('PENDING', 'RETRY') and next_attempt_at <= ?) or (state = 'PROCESSING' and processing_started_at <= ?))",
                timestamp(now), activityId, timestamp(now), timestamp(now.minusSeconds(300))) == 1;
    }
    @Override public void markSent(long activityId, Instant now) {
        jdbc.update("update activity_delivery set state = 'SENT', sent_at = ?, processing_started_at = null where activity_id = ?", timestamp(now), activityId);
    }
    @Override public void retry(long activityId, Instant nextAttemptAt) {
        jdbc.update("update activity_delivery set state = 'RETRY', next_attempt_at = ?, processing_started_at = null where activity_id = ?", timestamp(nextAttemptAt), activityId);
    }
    @Override public void markFailed(long activityId) {
        jdbc.update("update activity_delivery set state = 'FAILED', processing_started_at = null where activity_id = ?", activityId);
    }
    @Override public void markUncertain(long activityId) {
        jdbc.update("update activity_delivery set state = 'UNCERTAIN_SENT', processing_started_at = null where activity_id = ?", activityId);
    }
    @Override public void discard(long activityId) {
        jdbc.update("update activity_delivery set state = 'DISCARDED', processing_started_at = null where activity_id = ?", activityId);
    }
    @Override public void purgeExpired(Instant before) {
        jdbc.update("delete from activity_delivery where created_at < ?", timestamp(before));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
