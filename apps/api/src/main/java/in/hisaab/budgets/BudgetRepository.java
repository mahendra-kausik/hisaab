package in.hisaab.budgets;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

  /** One row of {@link #usage}. The quoted aliases below name these getters. */
  interface Usage {
    UUID getCategoryId();

    String getCategoryName();

    long getAmountPaise();

    long getSpentPaise();
  }

  Optional<Budget> findByWorkspaceIdAndCategoryIdAndEffectiveFrom(
      UUID workspaceId, UUID categoryId, LocalDate effectiveFrom);

  // Usage is computed, never stored. For each visible top-level expense category: the budget in
  // effect (latest effective_from on or before the period start; zero means none) and the live
  // expenses dated in the period on the category or its children. Transfers and income are
  // excluded by the type filter. Native SQL because transactions live in another package.
  @Query(
      value =
          """
          SELECT c.id AS "categoryId", c.name AS "categoryName",
                 b.amount_paise AS "amountPaise",
                 COALESCE((SELECT SUM(t.amount_paise)
                           FROM transactions t
                           JOIN categories tc ON tc.id = t.category_id
                           WHERE t.workspace_id = :workspaceId AND t.deleted_at IS NULL
                             AND t.type = 'expense'
                             AND t.occurred_on >= :start AND t.occurred_on < :end
                             AND (tc.id = c.id OR tc.parent_id = c.id)), 0)::bigint AS "spentPaise"
          FROM categories c
          JOIN LATERAL (SELECT amount_paise FROM budgets
                        WHERE workspace_id = :workspaceId AND category_id = c.id
                          AND effective_from <= :start
                        ORDER BY effective_from DESC LIMIT 1) b ON true
          WHERE c.workspace_id = :workspaceId AND c.kind = 'expense' AND c.parent_id IS NULL
            AND c.hidden_at IS NULL AND b.amount_paise > 0
          ORDER BY c.sort_order, c.name
          """,
      nativeQuery = true)
  List<Usage> usage(
      @Param("workspaceId") UUID workspaceId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);
}
