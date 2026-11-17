package in.hisaab.categories;

import in.hisaab.categories.dto.CategoryResponse;
import in.hisaab.categories.dto.CreateCategoryRequest;
import in.hisaab.categories.dto.UpdateCategoryRequest;
import in.hisaab.common.web.ConflictException;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceKind;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  private final CategoryRepository categories;
  private final WorkspaceAccess access;
  private final Clock clock;

  public CategoryService(CategoryRepository categories, WorkspaceAccess access, Clock clock) {
    this.categories = categories;
    this.access = access;
    this.clock = clock;
  }

  /** Flat list, expense before income, each in display order. Hidden ones are included. */
  @Transactional(readOnly = true)
  public List<CategoryResponse> list(UUID workspaceId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return categories.findByWorkspaceIdOrderByKindAscSortOrderAscNameAsc(workspaceId).stream()
        .map(CategoryResponse::from)
        .toList();
  }

  @Transactional
  public CategoryResponse create(UUID workspaceId, CreateCategoryRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    if (request.parentId() != null) {
      requireValidParent(workspaceId, request);
    }
    // New categories go last among their siblings. A workspace has a few dozen categories,
    // so computing the maximum in Java is simpler than a query with a nullable parameter.
    int next =
        categories.findByWorkspaceIdOrderByKindAscSortOrderAscNameAsc(workspaceId).stream()
                .filter(c -> c.getKind() == request.kind())
                .filter(c -> Objects.equals(c.getParentId(), request.parentId()))
                .mapToInt(Category::getSortOrder)
                .max()
                .orElse(-1)
            + 1;
    var category =
        new Category(
            workspaceId, request.parentId(), request.name().trim(), request.kind(), false, next);
    return CategoryResponse.from(saveUnique(category));
  }

  @Transactional
  public CategoryResponse update(UUID workspaceId, UUID categoryId, UpdateCategoryRequest patch) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var category = find(workspaceId, categoryId);
    if (patch.name() != null) {
      category.rename(patch.name().trim());
    }
    if (patch.hidden() != null) {
      category.setHidden(patch.hidden(), Instant.now(clock));
    }
    return CategoryResponse.from(saveUnique(category));
  }

  @Transactional
  public void delete(UUID workspaceId, UUID categoryId) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var category = find(workspaceId, categoryId);
    if (category.isSystem()) {
      throw new ConflictException(
          "system_category", "Default categories cannot be deleted; hide it instead");
    }
    categories.delete(category);
    try {
      // Flush now so a foreign key violation is caught here and becomes a 409. Today only child
      // categories reference a category; transactions and budgets will too, with no change here.
      categories.flush();
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "category_in_use", "This category is in use and cannot be deleted");
    }
  }

  /**
   * For transactions: the category must be in this workspace and of the given kind (an expense
   * cannot use an income category). Hidden categories are allowed. Called after the caller's own
   * access check.
   */
  @Transactional(readOnly = true)
  public void requireForType(UUID workspaceId, UUID categoryId, CategoryKind kind) {
    var category = categories.findByIdAndWorkspaceId(categoryId, workspaceId);
    if (category.isEmpty() || category.get().getKind() != kind) {
      throw new ValidationException(
          "invalid_category",
          "Category not found for this transaction type",
          Map.of("fields", Map.of("category_id", "Not a " + kind.json() + " category")));
    }
  }

  /** Seeds the default categories for a new workspace. Called by WorkspaceService.create. */
  @Transactional
  public void createDefaults(UUID workspaceId, WorkspaceKind workspaceKind) {
    for (var kind : CategoryKind.values()) {
      var seeds = DefaultCategories.forKind(workspaceKind, kind);
      for (int i = 0; i < seeds.size(); i++) {
        var seed = seeds.get(i);
        var parent = categories.save(new Category(workspaceId, null, seed.name(), kind, true, i));
        for (int j = 0; j < seed.children().size(); j++) {
          categories.save(
              new Category(workspaceId, parent.getId(), seed.children().get(j), kind, true, j));
        }
      }
    }
  }

  private Category find(UUID workspaceId, UUID categoryId) {
    // By id AND workspace: an id from another workspace is simply "not found".
    return categories
        .findByIdAndWorkspaceId(categoryId, workspaceId)
        .orElseThrow(() -> new NotFoundException("Category not found"));
  }

  // One level of nesting only; the database separately guarantees the parent is in the same
  // workspace and has the same kind (composite foreign key in V4).
  private void requireValidParent(UUID workspaceId, CreateCategoryRequest request) {
    var parent = categories.findByIdAndWorkspaceId(request.parentId(), workspaceId);
    if (parent.isEmpty()
        || parent.get().getKind() != request.kind()
        || parent.get().getParentId() != null) {
      throw new ValidationException(
          "invalid_parent",
          "Parent must be a top-level category of the same kind in this workspace",
          Map.of("fields", Map.of("parent_id", "Not a valid parent category")));
    }
  }

  // saveAndFlush so a duplicate-name violation surfaces here as a 409, not at commit as a 500.
  private Category saveUnique(Category category) {
    try {
      return categories.saveAndFlush(category);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("name_taken", "A category with this name already exists here");
    }
  }
}
