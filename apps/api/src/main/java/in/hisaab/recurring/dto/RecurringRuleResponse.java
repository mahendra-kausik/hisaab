package in.hisaab.recurring.dto;

import in.hisaab.recurring.Frequency;
import in.hisaab.recurring.Recurrence;
import in.hisaab.recurring.RecurringRule;
import in.hisaab.transactions.TransactionType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A rule plus its next few dates ({@code upcoming} is empty once the rule has ended). */
public record RecurringRuleResponse(
    @NotNull UUID id,
    @NotNull UUID accountId,
    @NotNull TransactionType type,
    @NotNull long amountPaise,
    UUID categoryId,
    UUID partyId,
    String note,
    @NotNull Frequency frequency,
    @NotNull int intervalCount,
    @NotNull LocalDate anchorDate,
    LocalDate endsOn,
    @NotNull boolean paused,
    @NotNull List<LocalDate> upcoming) {

  private static final int UPCOMING = 3;

  public static RecurringRuleResponse of(RecurringRule r) {
    return new RecurringRuleResponse(
        r.getId(),
        r.getAccountId(),
        r.getType(),
        r.getAmountPaise(),
        r.getCategoryId(),
        r.getPartyId(),
        r.getNote(),
        r.getFrequency(),
        r.getIntervalCount(),
        r.getAnchorDate(),
        r.getEndsOn(),
        r.isPaused(),
        Recurrence.upcoming(
            r.getAnchorDate(),
            r.getFrequency(),
            r.getIntervalCount(),
            r.getNextRunOn(),
            r.getEndsOn(),
            UPCOMING));
  }
}
