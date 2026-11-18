package in.hisaab.transactions.dto;

import in.hisaab.transactions.PaymentMethod;
import in.hisaab.transactions.TransactionType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Built straight from the list query (see TransactionQueries), not from the entity. transferGroupId
 * is set only on the two legs of a transfer; it is the id to use with the transfers endpoints.
 * categoryId, paymentMethod, note and transferGroupId can be null, so they are not marked
 * {@code @NotNull} and the generated TypeScript type keeps them optional.
 */
public record TransactionResponse(
    @NotNull UUID id,
    @NotNull UUID accountId,
    @NotNull TransactionType type,
    @NotNull long amountPaise,
    @NotNull LocalDate occurredOn,
    UUID categoryId,
    PaymentMethod paymentMethod,
    String note,
    @NotNull List<String> tags,
    UUID transferGroupId,
    @NotNull String source,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {}
