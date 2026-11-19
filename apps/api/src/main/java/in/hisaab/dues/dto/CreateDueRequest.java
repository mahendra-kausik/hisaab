package in.hisaab.dues.dto;

import in.hisaab.dues.DueDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Send {@code origin} to also record the money handed over, in the same step. */
public record CreateDueRequest(
    @NotNull UUID partyId,
    @NotNull DueDirection direction,
    @NotNull @Positive Long amountPaise,
    @NotNull @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String reason,
    LocalDate dueDate,
    UUID projectId,
    UUID categoryId,
    @Valid OriginTransactionRequest origin) {}
