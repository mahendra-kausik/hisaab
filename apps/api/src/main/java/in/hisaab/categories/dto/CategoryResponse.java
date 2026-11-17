package in.hisaab.categories.dto;

import in.hisaab.categories.Category;
import in.hisaab.categories.CategoryKind;
import java.time.Instant;
import java.util.UUID;

/** One category. Lists are flat; clients build the tree from {@code parentId}. */
public record CategoryResponse(
    UUID id,
    UUID parentId,
    String name,
    CategoryKind kind,
    boolean isSystem,
    Instant hiddenAt,
    int sortOrder) {

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
