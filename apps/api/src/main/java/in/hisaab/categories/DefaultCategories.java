package in.hisaab.categories;

import in.hisaab.workspaces.WorkspaceKind;
import java.util.List;

/**
 * The categories a new workspace starts with (see "Default Data" in project-overview.md). Order
 * matters: it becomes sort_order. The four loan categories are in every workspace kind, placed
 * before "Other".
 */
final class DefaultCategories {

  /** A top-level category and its (possibly empty) children. */
  record Seed(String name, List<String> children) {}

  private static Seed top(String name, String... children) {
    return new Seed(name, List.of(children));
  }

  private static final List<Seed> PERSONAL_EXPENSE =
      List.of(
          top("Food", "Groceries", "Eating Out", "Food Delivery"),
          top("Transport", "Fuel", "Cab/Auto", "Public Transport"),
          top("Housing", "Rent", "Maintenance", "Utilities"),
          top("Bills & Recharges", "Mobile", "Internet", "Electricity"),
          top("Shopping"),
          top("Health"),
          top("Education"),
          top("Entertainment"),
          top("Subscriptions"),
          top("Travel"),
          top("Personal Care"),
          top("Gifts & Donations"),
          top("Fees & Charges"),
          top("Money Lent"),
          top("Loan Repayment"),
          top("Other"));

  private static final List<Seed> PERSONAL_INCOME =
      List.of(
          top("Salary"),
          top("Stipend"),
          top("Business Income"),
          top("Refunds"),
          top("Interest"),
          top("Gifts Received"),
          top("Money Borrowed"),
          top("Repayment Received"),
          top("Other Income"));

  private static final List<Seed> BUSINESS_EXPENSE =
      List.of(
          top("Labour"),
          top("Materials"),
          top("Contractor Payments"),
          top("Broker Commission"),
          top("Registration & Legal"),
          top("Transport"),
          top("Office & Admin"),
          top("Money Lent"),
          top("Loan Repayment"),
          top("Other"));

  private static final List<Seed> BUSINESS_INCOME =
      List.of(
          top("Sale Proceeds"),
          top("Advance Received"),
          top("Rent Received"),
          top("Commission"),
          top("Money Borrowed"),
          top("Repayment Received"),
          top("Other Income"));

  private DefaultCategories() {}

  static List<Seed> forKind(WorkspaceKind workspaceKind, CategoryKind categoryKind) {
    boolean personal = workspaceKind == WorkspaceKind.PERSONAL;
    return categoryKind == CategoryKind.EXPENSE
        ? (personal ? PERSONAL_EXPENSE : BUSINESS_EXPENSE)
        : (personal ? PERSONAL_INCOME : BUSINESS_INCOME);
  }
}
