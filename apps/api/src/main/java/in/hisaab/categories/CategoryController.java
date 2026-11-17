package in.hisaab.categories;

import in.hisaab.categories.dto.CategoryResponse;
import in.hisaab.categories.dto.CreateCategoryRequest;
import in.hisaab.categories.dto.UpdateCategoryRequest;
import in.hisaab.common.web.ListResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public ListResponse<CategoryResponse> list(@PathVariable UUID workspaceId) {
    return ListResponse.of(categoryService.list(workspaceId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody CreateCategoryRequest request) {
    return categoryService.create(workspaceId, request);
  }

  @PatchMapping("/{categoryId}")
  public CategoryResponse update(
      @PathVariable UUID workspaceId,
      @PathVariable UUID categoryId,
      @Valid @RequestBody UpdateCategoryRequest request) {
    return categoryService.update(workspaceId, categoryId, request);
  }

  @DeleteMapping("/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID workspaceId, @PathVariable UUID categoryId) {
    categoryService.delete(workspaceId, categoryId);
  }
}
