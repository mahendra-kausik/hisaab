package in.hisaab.reports;

import in.hisaab.budgets.Period;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.reports.dto.MonthlyReport;
import in.hisaab.reports.dto.MonthlyReport.DaySpend;
import in.hisaab.users.UserRepository;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

  private final ReportQueries queries;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final Clock clock;

  public ReportService(
      ReportQueries queries,
      UserRepository users,
      WorkspaceAccess access,
      CurrentUser currentUser,
      Clock clock) {
    this.queries = queries;
    this.users = users;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Report for the period containing {@code day} (default: today in the user's timezone). */
  @Transactional(readOnly = true)
  public MonthlyReport monthly(UUID workspaceId, LocalDate day) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    var user = users.findById(currentUser.require()).orElseThrow();
    var target = day != null ? day : LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    var period = Period.containing(target, user.getPeriodStartDay());
    var prev = period.previous();
    var start = period.start();
    var end = period.endExclusive();

    Map<LocalDate, Long> spentByDay =
        queries.daily(workspaceId, start, end).stream()
            .collect(Collectors.toMap(DaySpend::day, DaySpend::spentPaise));
    List<DaySpend> daily = new ArrayList<>();
    for (var d = start; d.isBefore(end); d = d.plusDays(1)) {
      daily.add(new DaySpend(d, spentByDay.getOrDefault(d, 0L)));
    }

    return new MonthlyReport(
        start,
        period.lastDay(),
        queries.totals(workspaceId, start, end),
        queries.totals(workspaceId, prev.start(), prev.endExclusive()),
        queries.byCategory(workspaceId, start, end),
        daily,
        queries.topExpenses(workspaceId, start, end, 5));
  }
}
