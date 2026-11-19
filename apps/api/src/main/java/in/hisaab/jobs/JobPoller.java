package in.hisaab.jobs;

import in.hisaab.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs due jobs. {@code @Scheduled} makes Spring call {@link #runDue} every few seconds on its own
 * thread (switched on by {@link JobsConfig}); tests call it directly.
 */
@Component
public class JobPoller {

  private static final Logger log = LoggerFactory.getLogger(JobPoller.class);
  private static final int BATCH = 20;

  private final JobQueue queue;
  private final CurrentUser currentUser;
  private final Map<String, JobHandler> handlers;

  // Spring passes in every JobHandler bean, so a feature only has to implement the interface.
  public JobPoller(JobQueue queue, CurrentUser currentUser, List<JobHandler> handlers) {
    this.queue = queue;
    this.currentUser = currentUser;
    this.handlers =
        handlers.stream().collect(Collectors.toMap(JobHandler::kind, Function.identity()));
  }

  @Scheduled(fixedDelayString = "${app.jobs.poll-ms:5000}")
  public void runDue() {
    queue.requeueStale();
    for (var job : queue.claim(BATCH)) {
      try {
        var handler = handlers.get(job.kind());
        if (handler == null) {
          throw new IllegalStateException("No handler for job kind " + job.kind());
        }
        currentUser.runAs(job.userId(), () -> handler.handle(job.payload()));
        queue.done(job.id());
      } catch (Exception e) {
        log.warn("Job {} ({}) failed: {}", job.id(), job.kind(), e.toString());
        queue.fail(job.id(), e.toString());
      }
    }
  }
}
