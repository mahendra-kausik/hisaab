package in.hisaab.budgets;

import in.hisaab.budgets.dto.BudgetsResponse;
import in.hisaab.budgets.dto.SetBudgetRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/budgets")
public class BudgetController {

  private final BudgetService budgetService;

  public BudgetController(BudgetService budgetService) {
    this.budgetService = budgetService;
  }

  /** {@code period} is any date inside the wanted period; omitted means the current one. */
  @GetMapping
  public BudgetsResponse list(
      @PathVariable UUID workspaceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate period) {
    return budgetService.list(workspaceId, period);
  }

  @PutMapping("/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void set(
      @PathVariable UUID workspaceId,
      @PathVariable UUID categoryId,
      @Valid @RequestBody SetBudgetRequest request) {
    budgetService.set(workspaceId, categoryId, request.amountPaise());
  }
}
