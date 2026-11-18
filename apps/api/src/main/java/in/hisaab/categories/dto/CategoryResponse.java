package in.hisaab.categories.dto;

import in.hisaab.categories.Category;
import in.hisaab.categories.CategoryKind;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** One category. Lists are flat; clients build the tree from {@code parentId}. */
public record CategoryResponse(
    @NotNull UUID id,
    UUID parentId,
    @NotNull String name,
    @NotNull CategoryKind kind,
    @NotNull boolean isSystem,
    Instant hiddenAt,
    @NotNull int sortOrder) {

  public static CategoryResponse from(Category c) {
    return new CategoryResponse(
        c.getId(),
        c.getParentId(),
        c.getName(),
        c.getKind(),
        c.isSystem(),
        c.getHiddenAt(),
        c.getSortOrder());
  }
}
