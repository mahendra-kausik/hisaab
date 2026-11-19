package in.hisaab.budgets.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** One budgeted category in a period. Amounts are integer paise. */
public record BudgetItem(
    @NotNull UUID categoryId,
    @NotNull String categoryName,
    @NotNull long amountPaise,
    @NotNull long spentPaise) {}
