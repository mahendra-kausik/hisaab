package in.hisaab.recurring;

import in.hisaab.accounts.AccountService;
import in.hisaab.categories.CategoryKind;
import in.hisaab.categories.CategoryService;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.ListResponse;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.jobs.JobHandler;
import in.hisaab.jobs.JobQueue;
import in.hisaab.recurring.dto.RecurringRuleRequest;
import in.hisaab.recurring.dto.RecurringRuleResponse;
import in.hisaab.users.UserRepository;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recurring rules: the CRUD behind the API, and the {@code recurring.materialize} job that turns
 * due occurrences into transactions. The job is a chain: each run queues the next one for the day
 * of the next occurrence. Because the unique pair (recurring_rule_id, recurrence_date) rejects a
 * second insert, running a job twice, or catching up after downtime, never doubles an entry.
 */
@Service
public class RecurringService implements JobHandler {

  static final String KIND = "recurring.materialize";

  private final RecurringRuleRepository rules;
  private final AccountService accounts;
  private final CategoryService categories;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final JobQueue queue;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public RecurringService(
      RecurringRuleRepository rules,
      AccountService accounts,
      CategoryService categories,
      UserRepository users,
      WorkspaceAccess access,
      CurrentUser currentUser,
      JobQueue queue,
      JdbcTemplate jdbc,
      Clock clock) {
    this.rules = rules;
    this.accounts = accounts;
    this.categories = categories;
    this.users = users;
    this.access = access;
    this.currentUser = currentUser;
    this.queue = queue;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ListResponse<RecurringRuleResponse> list(UUID workspaceId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return ListResponse.of(
        rules.findByWorkspaceIdOrderByNextRunOnAsc(workspaceId).stream()
            .map(RecurringRuleResponse::of)
            .toList());
  }

  @Transactional
  public RecurringRuleResponse create(UUID workspaceId, RecurringRuleRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    validate(workspaceId, request);
    var rule = new RecurringRule(workspaceId, currentUser.require());
    rule.replace(
        request.accountId(),
        request.type(),
        request.amountPaise(),
        request.categoryId(),
        blankToNull(request.note()),
        request.frequency(),
        request.intervalCount(),
        request.anchorDate(),
        request.endsOn(),
        Instant.now(clock));
    // The first occurrence is the anchor itself; a past anchor is caught up by the job.
    rule.setNextRunOn(request.anchorDate());
    rule = rules.saveAndFlush(rule);
    runSoon(rule);
    return RecurringRuleResponse.of(rule);
  }

  /** PUT: replaces every editable field. Changing the schedule restarts it from today. */
  @Transactional
  public RecurringRuleResponse replace(UUID workspaceId, UUID id, RecurringRuleRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var rule = find(workspaceId, id);
    validate(workspaceId, request);
    boolean sameSchedule =
        rule.getFrequency() == request.frequency()
            && rule.getIntervalCount() == request.intervalCount()
            && rule.getAnchorDate().equals(request.anchorDate());
    rule.replace(
        request.accountId(),
        request.type(),
        request.amountPaise(),
        request.categoryId(),
        blankToNull(request.note()),
        request.frequency(),
        request.intervalCount(),
        request.anchorDate(),
        request.endsOn(),
        Instant.now(clock));
    if (!sameSchedule) {
      rule.setNextRunOn(firstOnOrAfterToday(rule, request.anchorDate().minusDays(1)));
    }
    rules.saveAndFlush(rule);
    runSoon(rule);
    return RecurringRuleResponse.of(rule);
  }

  @Transactional
  public RecurringRuleResponse pause(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var rule = find(workspaceId, id);
    if (!rule.isPaused()) {
      rule.setPausedAt(Instant.now(clock), Instant.now(clock));
    }
    return RecurringRuleResponse.of(rule);
  }

  /** Resuming skips the dates that passed while paused; they are not backfilled. */
  @Transactional
  public RecurringRuleResponse resume(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var rule = find(workspaceId, id);
    if (rule.isPaused()) {
      rule.setPausedAt(null, Instant.now(clock));
      rule.setNextRunOn(firstOnOrAfterToday(rule, rule.getNextRunOn().minusDays(1)));
      rules.saveAndFlush(rule);
      runSoon(rule);
    }
    return RecurringRuleResponse.of(rule);
  }

  /** Deleting a rule keeps the transactions it already made (their link is just cleared). */
  @Transactional
  public void delete(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    rules.delete(find(workspaceId, id));
  }

  @Override
  public String kind() {
    return KIND;
  }

  /** The job. Runs as the rule's creator; a rule that is gone, paused or finished is a no-op. */
  @Override
  @Transactional
  public void handle(String payload) {
    var rule = rules.findById(UUID.fromString(payload)).orElse(null);
    if (rule == null || rule.isPaused()) {
      return;
    }
    var zone = zoneOf(rule);
    var today = LocalDate.now(clock.withZone(zone));
    var next = rule.getNextRunOn();
    while (!next.isAfter(today) && !pastEnd(rule, next)) {
      jdbc.update(
          "INSERT INTO transactions (workspace_id, account_id, type, amount_paise, occurred_on,"
              + " category_id, note, created_by, recurring_rule_id, recurrence_date)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
              + " ON CONFLICT (recurring_rule_id, recurrence_date) DO NOTHING",
          rule.getWorkspaceId(),
          rule.getAccountId(),
          rule.getType().json(),
          rule.getAmountPaise(),
          next,
          rule.getCategoryId(),
          rule.getNote(),
          rule.getCreatedBy(),
          rule.getId(),
          next);
      next = nextAfter(rule, next);
    }
    rule.setNextRunOn(next);
    if (!pastEnd(rule, next)) {
      queue.enqueue(
          rule.getCreatedBy(),
          KIND,
          rule.getId().toString(),
          dedupeKey(rule),
          next.atStartOfDay(zone).toInstant());
    }
  }

  private void runSoon(RecurringRule rule) {
    queue.enqueue(currentUser.require(), KIND, rule.getId().toString(), dedupeKey(rule), null);
  }

  private static String dedupeKey(RecurringRule rule) {
    return "recurring:" + rule.getId();
  }

  private static boolean pastEnd(RecurringRule rule, LocalDate date) {
    return rule.getEndsOn() != null && date.isAfter(rule.getEndsOn());
  }

  private static LocalDate nextAfter(RecurringRule rule, LocalDate date) {
    return Recurrence.after(
        rule.getAnchorDate(), rule.getFrequency(), rule.getIntervalCount(), date);
  }

  /** First occurrence after both {@code floor} and yesterday: nothing in the past is created. */
  private LocalDate firstOnOrAfterToday(RecurringRule rule, LocalDate floor) {
    var yesterday = LocalDate.now(clock.withZone(zoneOf(rule))).minusDays(1);
    return nextAfter(rule, floor.isAfter(yesterday) ? floor : yesterday);
  }

  private ZoneId zoneOf(RecurringRule rule) {
    return ZoneId.of(users.findById(rule.getCreatedBy()).orElseThrow().getTimezone());
  }

  private void validate(UUID workspaceId, RecurringRuleRequest request) {
    if (request.type().isTransfer()) {
      throw new ValidationException(
          "invalid_type",
          "Recurring transfers are not supported",
          Map.of("fields", Map.of("type", "Must be expense or income")));
    }
    if (request.endsOn() != null && request.endsOn().isBefore(request.anchorDate())) {
      throw new ValidationException(
          "invalid_end",
          "The end date is before the first date",
          Map.of("fields", Map.of("ends_on", "Must not be before the start date")));
    }
    accounts.requireOpen(workspaceId, request.accountId());
    if (request.categoryId() != null) {
      categories.requireForType(
          workspaceId, request.categoryId(), CategoryKind.valueOf(request.type().name()));
    }
  }

  private RecurringRule find(UUID workspaceId, UUID id) {
    return rules
        .findByIdAndWorkspaceId(id, workspaceId)
        .orElseThrow(() -> new NotFoundException("Recurring rule not found"));
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
