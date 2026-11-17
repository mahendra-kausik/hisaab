package in.hisaab.transactions.dto;

import in.hisaab.transactions.PaymentMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One transfer, assembled from its two legs. {@code id} is the shared transfer_group_id, which is
 * what the /transfers endpoints take; the leg ids are included so a client can match them to rows
 * in the transactions list.
 */
public record TransferResponse(
    UUID id,
    UUID fromAccountId,
    UUID toAccountId,
    long amountPaise,
    LocalDate occurredOn,
    PaymentMethod paymentMethod,
    String note,
    UUID outTransactionId,
    UUID inTransactionId,
    Instant createdAt,
    Instant updatedAt) {

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
