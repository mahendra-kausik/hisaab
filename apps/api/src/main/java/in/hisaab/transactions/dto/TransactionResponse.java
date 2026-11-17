package in.hisaab.transactions.dto;

import in.hisaab.transactions.PaymentMethod;
import in.hisaab.transactions.TransactionType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Built straight from the list query (see TransactionQueries), not from the entity. */
public record TransactionResponse(
    UUID id,
    UUID accountId,
    TransactionType type,
    long amountPaise,
    LocalDate occurredOn,
    UUID categoryId,
    PaymentMethod paymentMethod,
    String note,
    List<String> tags,
    String source,
    Instant createdAt,
    Instant updatedAt) {}
