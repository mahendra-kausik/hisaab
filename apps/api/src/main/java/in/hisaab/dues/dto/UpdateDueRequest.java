package in.hisaab.dues.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PUT body: replaces every editable field; optional ones left out are cleared. The party and
 * direction cannot change; cancel the due and create a new one instead.
 */
public record UpdateDueRequest(
    @NotNull @Positive Long amountPaise,
    @NotNull @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String reason,
    LocalDate dueDate,
    UUID projectId,
    UUID categoryId) {}
