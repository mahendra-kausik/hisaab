package in.hisaab.categories.dto;

import in.hisaab.categories.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code parentId} is optional: leave it out for a top-level category. */
public record CreateCategoryRequest(
    @NotBlank @Size(max = 100) String name, @NotNull CategoryKind kind, UUID parentId) {}
