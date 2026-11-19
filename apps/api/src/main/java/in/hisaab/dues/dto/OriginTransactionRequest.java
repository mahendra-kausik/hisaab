package in.hisaab.dues.dto;

import in.hisaab.transactions.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The money handed over when the due is created (a loan given or taken). Amount, party, project and
 * type come from the due; the client picks the account, date and category (Money Lent / Money
 * Borrowed in the default categories).
 */
public record OriginTransactionRequest(
    @NotNull UUID accountId,
    @NotNull LocalDate occurredOn,
    UUID categoryId,
    PaymentMethod paymentMethod,
    @Size(max = 500) String note) {}
