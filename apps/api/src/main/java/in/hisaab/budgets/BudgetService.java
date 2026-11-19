package in.hisaab.budgets;

import in.hisaab.budgets.dto.BudgetItem;
import in.hisaab.budgets.dto.BudgetsResponse;
import in.hisaab.categories.CategoryKind;
import in.hisaab.categories.CategoryRepository;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.users.User;
import in.hisaab.users.UserRepository;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

  private final BudgetRepository budgets;
  private final CategoryRepository categories;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final Clock clock;

  public BudgetService(
      BudgetRepository budgets,
      CategoryRepository categories,
      UserRepository users,
      WorkspaceAccess access,
      CurrentUser currentUser,
      Clock clock) {
    this.budgets = budgets;
    this.categories = categories;
    this.users = users;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Budgets for the period containing {@code day} (default: today in the user's timezone). */
  @Transactional(readOnly = true)
  public BudgetsResponse list(UUID workspaceId, LocalDate day) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    var user = user();
    var period = Period.containing(day != null ? day : today(user), user.getPeriodStartDay());
    var items =
        budgets.usage(workspaceId, period.start(), period.endExclusive()).stream()
            .map(u ->
                new BudgetItem(
                    u.getCategoryId(), u.getCategoryName(), u.getAmountPaise(), u.getSpentPaise()))
            .toList();
    return new BudgetsResponse(period.start(), period.lastDay(), items);
  }

  /** Sets the budget from the current period on; earlier periods keep their amounts. */
  @Transactional
  public void set(UUID workspaceId, UUID categoryId, long amountPaise) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var category =
        categories
            .findByIdAndWorkspaceId(categoryId, workspaceId)
            .orElseThrow(() -> new NotFoundException("Category not found"));
    if (category.getKind() != CategoryKind.EXPENSE || category.getParentId() != null) {
      throw new ValidationException(
          "invalid_category",
          "Budgets are set on top-level expense categories",
          Map.of("fields", Map.of("category_id", "Not a top-level expense category")));
    }
    var user = user();
    var from = Period.containing(today(user), user.getPeriodStartDay()).start();
    var existing =
        budgets.findByWorkspaceIdAndCategoryIdAndEffectiveFrom(workspaceId, categoryId, from);
    if (existing.isPresent()) {
      existing.get().setAmountPaise(amountPaise);
    } else {
      budgets.save(new Budget(workspaceId, categoryId, amountPaise, from));
    }
  }

  private LocalDate today(User user) {
    return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
  }

  private User user() {
    return users.findById(currentUser.require()).orElseThrow();
  }
}
