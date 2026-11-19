package in.hisaab.budgets.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Zero removes the budget from the current period on. */
public record SetBudgetRequest(@NotNull @Min(0) @Max(1_000_000_000_000L) Long amountPaise) {}
