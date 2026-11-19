package in.hisaab.dues.dto;

import in.hisaab.dues.Due;
import in.hisaab.dues.DueDirection;
import in.hisaab.dues.DueState;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * paidPaise is the sum of live transactions linked to the due; outstandingPaise is amount minus
 * paid. dueDate, projectId, categoryId, originTransactionId and cancelledAt can be null.
 */
public record DueResponse(
    @NotNull UUID id,
    @NotNull UUID partyId,
    @NotNull DueDirection direction,
    @NotNull long amountPaise,
    @NotNull long paidPaise,
    @NotNull long outstandingPaise,
    @NotNull String reason,
    LocalDate dueDate,
    UUID projectId,
    UUID categoryId,
    UUID originTransactionId,
    @NotNull DueState state,
    Instant cancelledAt,
    @NotNull Instant updatedAt) {

  public static DueResponse of(Due d, long paidPaise, LocalDate today) {
    return new DueResponse(
        d.getId(),
        d.getPartyId(),
        d.getDirection(),
        d.getAmountPaise(),
        paidPaise,
        d.getAmountPaise() - paidPaise,
        d.getReason(),
        d.getDueDate(),
        d.getProjectId(),
        d.getCategoryId(),
        d.getOriginTransactionId(),
        DueState.of(d, paidPaise, today),
        d.getCancelledAt(),
        d.getUpdatedAt());
  }
}
