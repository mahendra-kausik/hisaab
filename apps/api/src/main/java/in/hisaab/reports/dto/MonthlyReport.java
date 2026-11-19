package in.hisaab.reports.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One period's report. Money is integer paise. {@code periodEnd} is the last day (inclusive).
 * Transfers are never counted, so income minus expense is the real net.
 */
public record MonthlyReport(
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd,
    @NotNull Totals totals,
    @NotNull Totals previous,
    @NotNull List<CategorySpend> byCategory,
    @NotNull List<DaySpend> daily,
    @NotNull List<TopExpense> topExpenses) {

  public record Totals(
      @NotNull long incomePaise, @NotNull long expensePaise, @NotNull long netPaise) {
    public static Totals of(long income, long expense) {
      return new Totals(income, expense, income - expense);
    }
  }

  /** Expense rolled up to its top-level category; a null id means uncategorised. */
  public record CategorySpend(
      UUID categoryId, @NotNull String categoryName, @NotNull long spentPaise) {}

  /** Every day of the period appears, with 0 when nothing was spent. */
  public record DaySpend(@NotNull LocalDate day, @NotNull long spentPaise) {}

  public record TopExpense(
      @NotNull UUID id,
      @NotNull LocalDate occurredOn,
      @NotNull long amountPaise,
      String categoryName,
      String note) {}
}
