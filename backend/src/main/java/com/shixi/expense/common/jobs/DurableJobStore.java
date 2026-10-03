package com.shixi.expense.common.jobs;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Small database-backed delivery queues; table names are a closed enum, never user input. */
@Repository
public class DurableJobStore {
    public enum Queue {
        NOTIFICATION("notification_outbox", "payload"), CLEANUP("attachment_cleanup_job", "storage_path");
        final String table;
        final String payloadColumn;
        Queue(String table, String payloadColumn) { this.table = table; this.payloadColumn = payloadColumn; }
    }
    public record Job(long id, String payload, int attempts, String token) { }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;

    public DurableJobStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void enqueueNotification(String key, Long recipientId, String payload) {
        jdbc.update("INSERT INTO notification_outbox (message_key, recipient_id, payload) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE id = id", key, recipientId, payload);
    }

    /** Commit the recovery intent before writing bytes; upload locks this row until metadata commit. */
    public long stageUpload(String path) {
        return independent.execute(status -> enqueueCleanup(path, LocalDateTime.now().plusMinutes(15)));
    }

    public long enqueueCleanup(String path, LocalDateTime availableAt) {
        var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO attachment_cleanup_job (storage_path, available_at) VALUES (?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, path);
            statement.setTimestamp(2, Timestamp.valueOf(availableAt));
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    public void protectUpload(long id) {
        jdbc.queryForObject("SELECT id FROM attachment_cleanup_job WHERE id = ? FOR UPDATE", Long.class, id);
    }

    public void finishUpload(long id) {
        jdbc.update("UPDATE attachment_cleanup_job SET status = 'DONE' WHERE id = ?", id);
    }

    public void completeRecovery(long id) {
        independent.executeWithoutResult(status -> finishUpload(id));
    }

    public List<Job> claim(Queue queue, int limit) {
        LocalDateTime now = LocalDateTime.now();
        String due = "((status = 'PENDING' AND available_at <= ?) OR (status = 'PROCESSING' AND lease_until <= ?))";
        List<Job> candidates = jdbc.query("SELECT id, " + queue.payloadColumn
                + " AS payload, attempts FROM " + queue.table + " WHERE " + due + " ORDER BY id LIMIT ?",
                (row, index) -> new Job(row.getLong("id"), row.getString("payload"), row.getInt("attempts"), null),
                now, now, limit);
        List<Job> claimed = new ArrayList<>();
        for (Job candidate : candidates) {
            // Workers request one job per tick so leases are not consumed waiting for earlier network calls.
            String token = UUID.randomUUID().toString();
            int changed = jdbc.update("UPDATE " + queue.table
                    + " SET status = 'PROCESSING', claim_token = ?, lease_until = ?, attempts = attempts + 1"
                    + " WHERE id = ? AND " + due, token, now.plusSeconds(60), candidate.id(), now, now);
            if (changed == 1) claimed.add(new Job(candidate.id(), candidate.payload(), candidate.attempts() + 1, token));
        }
        return claimed;
    }

    public void complete(Queue queue, Job job) {
        String sent = queue == Queue.NOTIFICATION ? ", sent_at = CURRENT_TIMESTAMP(3)" : "";
        jdbc.update("UPDATE " + queue.table + " SET status = 'DONE', claim_token = NULL, lease_until = NULL"
                + sent + " WHERE id = ? AND claim_token = ? AND status = 'PROCESSING'", job.id(), job.token());
    }

    public void retry(Queue queue, Job job, String safeError) {
        long delay = Math.min(300, 1L << Math.min(job.attempts(), 8));
        jdbc.update("UPDATE " + queue.table
                + " SET status = 'PENDING', available_at = ?, claim_token = NULL, lease_until = NULL, last_error = ?"
                + " WHERE id = ? AND claim_token = ? AND status = 'PROCESSING'",
                LocalDateTime.now().plusSeconds(delay), safeError, job.id(), job.token());
    }
}
