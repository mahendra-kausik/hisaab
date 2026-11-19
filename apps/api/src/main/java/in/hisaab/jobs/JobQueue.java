package in.hisaab.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The Postgres-backed queue: enqueue, claim, complete, retry. Plain SQL through {@link
 * JdbcTemplate}; {@code jobs} has no RLS (see V8), so none of this needs a current user.
 *
 * <p>{@link #enqueue} joins the caller's transaction, so a job exists exactly when the change that
 * wanted it was committed.
 */
@Component
public class JobQueue {

  static final int MAX_ATTEMPTS = 5;

  /** A claimed job. {@code payload} is a single text value (an id), kept as a JSON string. */
  public record Job(UUID id, UUID userId, String kind, String payload) {}

  private final JdbcTemplate jdbc;

  public JobQueue(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Queues a job for {@code runAt}; null means "now", stamped by the database so the app's and the
   * database's clocks can never disagree about whether it is due. With a {@code dedupeKey}, a job
   * already queued under that key is reused (moved earlier if the new time is sooner) instead of
   * adding a second one.
   */
  public void enqueue(UUID userId, String kind, String payload, String dedupeKey, Instant runAt) {
    jdbc.update(
        "INSERT INTO jobs (user_id, kind, payload, dedupe_key, run_at)"
            + " VALUES (?, ?, to_jsonb(?::text), ?, COALESCE(?::timestamptz, now()))"
            + " ON CONFLICT (dedupe_key) WHERE status = 'queued' AND dedupe_key IS NOT NULL"
            + " DO UPDATE SET run_at = LEAST(jobs.run_at, EXCLUDED.run_at)",
        userId,
        kind,
        payload,
        dedupeKey,
        runAt == null ? null : java.sql.Timestamp.from(runAt));
  }

  /**
   * Takes up to {@code limit} due jobs. {@code FOR UPDATE SKIP LOCKED} lets several workers claim
   * at once without ever getting the same row, and the UPDATE marks them running in one step.
   */
  List<Job> claim(int limit) {
    return jdbc.query(
        "UPDATE jobs SET status = 'running', locked_at = now(), attempts = attempts + 1"
            + " WHERE id IN (SELECT id FROM jobs WHERE status = 'queued' AND run_at <= now()"
            + "              ORDER BY run_at LIMIT ? FOR UPDATE SKIP LOCKED)"
            + " RETURNING id, user_id, kind, payload #>> '{}'",
        (rs, i) ->
            new Job(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getString(4)),
        limit);
  }

  void done(UUID id) {
    jdbc.update("UPDATE jobs SET status = 'done', locked_at = NULL WHERE id = ?", id);
  }

  /** Back to the queue after attempts^2 minutes, or {@code failed} once attempts run out. */
  void fail(UUID id, String error) {
    jdbc.update(
        "UPDATE jobs SET last_error = ?, locked_at = NULL,"
            + " status = CASE WHEN attempts >= ? THEN 'failed' ELSE 'queued' END,"
            + " run_at = now() + attempts * attempts * interval '1 minute' WHERE id = ?",
        error,
        MAX_ATTEMPTS,
        id);
  }

  /** A worker that died mid-job leaves it running; after 10 minutes it goes back in the queue. */
  void requeueStale() {
    jdbc.update(
        "UPDATE jobs j SET status = 'queued', locked_at = NULL"
            + " WHERE j.status = 'running' AND j.locked_at < now() - interval '10 minutes'"
            + " AND NOT EXISTS (SELECT 1 FROM jobs q WHERE q.status = 'queued'"
            + "                 AND q.dedupe_key = j.dedupe_key)");
  }
}
