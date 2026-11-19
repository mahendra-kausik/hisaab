package in.hisaab.transactions.dto;

import in.hisaab.transactions.PaymentMethod;
import in.hisaab.transactions.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body for both POST (create) and PUT (full replace): optional fields left out are cleared on PUT.
 * Tags are plain names; unknown ones are created on the fly.
 */
public record TransactionRequest(
    @NotNull UUID accountId,
    @NotNull TransactionType type,
    @NotNull @Positive Long amountPaise,
    @NotNull LocalDate occurredOn,
    UUID categoryId,
    UUID partyId,
    UUID projectId,
    UUID dueId,
    PaymentMethod paymentMethod,
    @Size(max = 500) String note,
    @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags) {}
