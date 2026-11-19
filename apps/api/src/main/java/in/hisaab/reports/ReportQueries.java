package in.hisaab.reports;

import in.hisaab.reports.dto.MonthlyReport.CategorySpend;
import in.hisaab.reports.dto.MonthlyReport.DaySpend;
import in.hisaab.reports.dto.MonthlyReport.TopExpense;
import in.hisaab.reports.dto.MonthlyReport.Totals;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hand-written SQL for reports. Every query counts only live rows of type income/expense dated in
 * [start, end), so transfers and soft-deleted rows can never leak into a total. Runs inside the
 * service's transaction, so RLS applies.
 */
@Repository
public class ReportQueries {

  private static final String LIVE =
      " t.workspace_id = ? AND t.deleted_at IS NULL AND t.occurred_on >= ? AND t.occurred_on < ?";

  private final JdbcTemplate jdbc;

  public ReportQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Totals totals(UUID workspaceId, LocalDate start, LocalDate end) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(t.amount_paise) FILTER (WHERE t.type = 'income'), 0)::bigint,
               COALESCE(SUM(t.amount_paise) FILTER (WHERE t.type = 'expense'), 0)::bigint
        FROM transactions t WHERE\
        """
            + LIVE,
        (rs, i) -> Totals.of(rs.getLong(1), rs.getLong(2)),
        workspaceId,
        start,
        end);
  }

  /** Expense per top-level category (children roll up), biggest first. */
  public List<CategorySpend> byCategory(UUID workspaceId, LocalDate start, LocalDate end) {
    return jdbc.query(
        """
        SELECT COALESCE(p.id, c.id) AS id, COALESCE(p.name, c.name, 'Uncategorized') AS name,
               SUM(t.amount_paise)::bigint AS spent
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN categories p ON p.id = c.parent_id
        WHERE t.type = 'expense' AND\
        """
            + LIVE
            + " GROUP BY 1, 2 ORDER BY spent DESC, name",
        (rs, i) ->
            new CategorySpend(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("spent")),
        workspaceId,
        start,
        end);
  }

  /** Days with spending only; the service fills the gaps. */
  public List<DaySpend> daily(UUID workspaceId, LocalDate start, LocalDate end) {
    return jdbc.query(
        "SELECT t.occurred_on, SUM(t.amount_paise)::bigint FROM transactions t"
            + " WHERE t.type = 'expense' AND"
            + LIVE
            + " GROUP BY t.occurred_on",
        (rs, i) -> new DaySpend(rs.getObject(1, LocalDate.class), rs.getLong(2)),
        workspaceId,
        start,
        end);
  }

  public List<TopExpense> topExpenses(UUID workspaceId, LocalDate start, LocalDate end, int limit) {
    return jdbc.query(
        """
        SELECT t.id, t.occurred_on, t.amount_paise, c.name AS category_name, t.note
        FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.type = 'expense' AND\
        """
            + LIVE
            + " ORDER BY t.amount_paise DESC, t.occurred_on DESC, t.id LIMIT ?",
        (rs, i) ->
            new TopExpense(
                rs.getObject("id", UUID.class),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getLong("amount_paise"),
                rs.getString("category_name"),
                rs.getString("note")),
        workspaceId,
        start,
        end,
        limit);
  }
}
