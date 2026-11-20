package in.hisaab.dues;

import in.hisaab.jobs.JobHandler;
import in.hisaab.jobs.JobQueue;
import in.hisaab.users.UserRepository;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily email to the user listing overdue, due-today and next-3-days dues across every
 * workspace they belong to. One job per user that re-queues itself for the next send time (08:00 in
 * the user's timezone). Never contacts a party.
 *
 * <p>Runs as the user, so row-level security limits the query to their workspaces. The send and the
 * {@code dues_digests} claim share one transaction: if the send throws, the claim is rolled back
 * and the poller's retry sends again; if the job runs twice, the claim stops a second email.
 */
@Service
public class DuesDigestJob implements JobHandler {

  public static final String KIND = "dues.digest";

  private static final Logger log = LoggerFactory.getLogger(DuesDigestJob.class);
  private static final int LOOKAHEAD_DAYS = 3;
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  private record Row(
      String workspace,
      String party,
      boolean receivable,
      long outstandingPaise,
      LocalDate dueDate,
      String reason) {}

  private final JdbcTemplate jdbc;
  private final UserRepository users;
  private final JobQueue queue;
  private final JavaMailSender mail;
  private final Clock clock;
  private final String from;
  private final int sendHour;
  private final String webOrigin;

  public DuesDigestJob(
      JdbcTemplate jdbc,
      UserRepository users,
      JobQueue queue,
      JavaMailSender mail,
      Clock clock,
      @Value("${app.mail.from:}") String from,
      @Value("${app.dues-digest.hour:8}") int sendHour,
      @Value("${app.web-origin}") String webOrigin) {
    this.jdbc = jdbc;
    this.users = users;
    this.queue = queue;
    this.mail = mail;
    this.clock = clock;
    this.from = from;
    this.sendHour = sendHour;
    this.webOrigin = webOrigin;
  }

  /** The key a user's digest job is queued under: one queued job per user. */
  public static String dedupeKey(UUID userId) {
    return KIND + ":" + userId;
  }

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  @Transactional
  public void handle(String payload) {
    var userId = UUID.fromString(payload);
    var user = users.findById(userId).orElse(null);
    if (user == null) {
      return;
    }
    var zone = ZoneId.of(user.getTimezone());
    var now = ZonedDateTime.now(clock.withZone(zone));
    var today = now.toLocalDate();
    var sendAt = today.atTime(sendHour, 0).atZone(zone);

    if (!now.isBefore(sendAt) && user.isDuesDigestEnabled()) {
      if (from.isBlank()) {
        log.debug("Dues digest skipped: app.mail.from (MAIL_FROM) is not set");
      } else {
        sendOnce(userId, user.getName(), user.getEmail(), today);
      }
    }
    // Before today's send time: come back then. Otherwise: tomorrow. Queued last, so a failed
    // send (an exception above) leaves the retry to the poller instead of skipping a day.
    var next = now.isBefore(sendAt) ? sendAt : today.plusDays(1).atTime(sendHour, 0).atZone(zone);
    queue.enqueue(userId, KIND, payload, dedupeKey(userId), next.toInstant());
  }

  private void sendOnce(UUID userId, String name, String email, LocalDate today) {
    int claimed =
        jdbc.update(
            "INSERT INTO dues_digests (user_id, digest_date) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING",
            userId,
            today);
    if (claimed == 0) {
      return;
    }
    var rows = query(today);
    if (rows.isEmpty()) {
      return;
    }
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject(subject(rows, today));
    message.setText(body(name, rows, today));
    mail.send(message);
  }

  /**
   * Open dues (not cancelled, not fully paid) with a due date up to 3 days away, oldest first. Paid
   * = live transactions linked to the due, as in {@code DueRepository.paidByWorkspace}.
   */
  private List<Row> query(LocalDate today) {
    return jdbc.query(
        """
        SELECT w.name, p.name, d.direction, d.amount_paise - COALESCE(paid.total, 0),
               d.due_date, d.reason
        FROM dues d
        JOIN workspaces w ON w.id = d.workspace_id
        JOIN parties p ON p.id = d.party_id AND p.workspace_id = d.workspace_id
        LEFT JOIN (SELECT due_id, SUM(amount_paise) AS total FROM transactions
                   WHERE due_id IS NOT NULL AND deleted_at IS NULL GROUP BY due_id) paid
               ON paid.due_id = d.id
        WHERE d.deleted_at IS NULL AND d.cancelled_at IS NULL AND d.due_date IS NOT NULL
          AND d.due_date <= ? AND d.amount_paise > COALESCE(paid.total, 0)
        ORDER BY d.due_date, w.name, p.name, d.created_at
        """,
        (rs, i) ->
            new Row(
                rs.getString(1),
                rs.getString(2),
                "receivable".equals(rs.getString(3)),
                rs.getLong(4),
                rs.getObject(5, LocalDate.class),
                rs.getString(6)),
        today.plusDays(LOOKAHEAD_DAYS));
  }

  private static String subject(List<Row> rows, LocalDate today) {
    long overdue = rows.stream().filter(r -> r.dueDate().isBefore(today)).count();
    long dueToday = rows.stream().filter(r -> r.dueDate().isEqual(today)).count();
    var parts = new java.util.ArrayList<String>();
    if (overdue > 0) {
      parts.add(overdue + " overdue");
    }
    if (dueToday > 0) {
      parts.add(dueToday + " due today");
    }
    if (parts.isEmpty()) {
      parts.add(rows.size() + " coming up");
    }
    return "Hisaab: " + String.join(", ", parts);
  }

  private String body(String name, List<Row> rows, LocalDate today) {
    var text = new StringBuilder("Hi " + name + ",\n");
    section(text, "Overdue", rows, r -> r.dueDate().isBefore(today));
    section(text, "Due today", rows, r -> r.dueDate().isEqual(today));
    section(text, "Next 3 days", rows, r -> r.dueDate().isAfter(today));
    text.append("\nOpen your dues: ").append(webOrigin).append("/dues\n");
    text.append("Turn this email off in Settings.\n");
    return text.toString();
  }

  private static void section(
      StringBuilder text, String title, List<Row> rows, java.util.function.Predicate<Row> in) {
    var mine = rows.stream().filter(in).toList();
    if (mine.isEmpty()) {
      return;
    }
    text.append("\n").append(title).append(" (").append(mine.size()).append(")\n");
    var rupees = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    for (var r : mine) {
      text.append("- ")
          .append(r.party())
          .append(": ")
          .append(rupees.format(r.outstandingPaise() / 100.0))
          .append(r.receivable() ? " owed to you" : " you owe")
          .append(", due ")
          .append(r.dueDate().format(DAY))
          .append(" - ")
          .append(r.reason())
          .append(" [")
          .append(r.workspace())
          .append("]\n");
    }
  }
}
