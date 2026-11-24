package in.hisaab.categories;

import in.hisaab.workspaces.WorkspaceKind;
import java.util.List;

/**
 * The categories a new workspace starts with (see "Default Data" in project-overview.md). Only
 * expense categories exist (income has none), and there are no default subcategories: users add
 * their own. Order matters: it becomes sort_order.
 */
final class DefaultCategories {

  private static final List<String> PERSONAL =
      List.of(
          "Food",
          "Transport",
          "Bills & Recharges",
          "Shopping",
          "Education",
          "Entertainment",
          "Subscriptions",
          "Other");

  private static final List<String> BUSINESS =
      List.of(
          "Labour",
          "Materials",
          "Contractor Payments",
          "Broker Commission",
          "Registration & Legal",
          "Transport",
          "Office & Admin",
          "Other");

  private DefaultCategories() {}

  static List<String> forKind(WorkspaceKind workspaceKind) {
    return workspaceKind == WorkspaceKind.PERSONAL ? PERSONAL : BUSINESS;
  }
}
