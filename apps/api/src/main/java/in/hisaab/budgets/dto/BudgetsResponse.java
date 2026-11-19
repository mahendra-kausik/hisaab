package in.hisaab.budgets.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Budgets for one period; {@code periodEnd} is the period's last day (inclusive). */
public record BudgetsResponse(
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd,
    @NotNull List<BudgetItem> items) {}
