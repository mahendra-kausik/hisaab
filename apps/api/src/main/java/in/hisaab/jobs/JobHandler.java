package in.hisaab.jobs;

/**
 * Implemented by a feature to run one kind of job. Handlers must be idempotent: a job can run twice
 * (a retry, a crash after the work but before it was marked done), and the result must be the same.
 */
public interface JobHandler {

  /** The {@code kind} this handler is responsible for, e.g. {@code recurring.materialize}. */
  String kind();

  /**
   * Runs the job as the user who enqueued it, so row-level security applies. Throwing makes the
   * poller retry later with a growing delay.
   */
  void handle(String payload);
}
