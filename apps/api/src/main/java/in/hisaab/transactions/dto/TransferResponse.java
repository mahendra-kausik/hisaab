package in.hisaab.transactions.dto;

import in.hisaab.transactions.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One transfer, assembled from its two legs. {@code id} is the shared transfer_group_id, which is
 * what the /transfers endpoints take; the leg ids are included so a client can match them to rows
 * in the transactions list.
 */
public record TransferResponse(
    @NotNull UUID id,
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @NotNull long amountPaise,
    @NotNull LocalDate occurredOn,
    PaymentMethod paymentMethod,
    String note,
    @NotNull UUID outTransactionId,
    @NotNull UUID inTransactionId,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {

  public static TransferResponse from(TransactionResponse out, TransactionResponse in) {
    return new TransferResponse(
        out.transferGroupId(),
        out.accountId(),
        in.accountId(),
        out.amountPaise(),
        out.occurredOn(),
        out.paymentMethod(),
        out.note(),
        out.id(),
        in.id(),
        out.createdAt(),
        out.updatedAt());
  }
}
