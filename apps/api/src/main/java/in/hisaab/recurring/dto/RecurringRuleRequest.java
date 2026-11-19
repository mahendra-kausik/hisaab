package in.hisaab.recurring.dto;

import in.hisaab.recurring.Frequency;
import in.hisaab.transactions.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for POST and PUT (full replace). {@code anchorDate} is the first occurrence; a date in the
 * past makes the job create the missed entries.
 */
public record RecurringRuleRequest(
    @NotNull UUID accountId,
    @NotNull TransactionType type,
    @NotNull @Positive Long amountPaise,
    UUID categoryId,
    @Size(max = 500) String note,
    @NotNull Frequency frequency,
    @NotNull @Min(1) @Max(52) Integer intervalCount,
    @NotNull LocalDate anchorDate,
    LocalDate endsOn) {}
