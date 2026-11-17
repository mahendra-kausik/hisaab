package in.hisaab.transactions.dto;

import in.hisaab.transactions.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for both POST (create) and PUT (full replace) of a transfer. The note and payment method are
 * copied onto both legs; optional fields left out are cleared on PUT. No category or tags: a
 * transfer is not income or expense.
 */
public record TransferRequest(
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @NotNull @Positive Long amountPaise,
    @NotNull LocalDate occurredOn,
    PaymentMethod paymentMethod,
    @Size(max = 500) String note) {}
