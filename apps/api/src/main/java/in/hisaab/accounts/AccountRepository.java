package in.hisaab.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring derives the simple queries from the method names; the balance sums are written out. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

  /** One row of {@link #netFlowsByWorkspace}. The quoted aliases below name these getters. */
  interface NetFlow {
    UUID getAccountId();

    long getNetPaise();
  }

  List<Account> findByWorkspaceIdOrderByName(UUID workspaceId);

  Optional<Account> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

  // Balances are computed, never stored: inflows minus outflows over live (not soft-deleted)
  // transactions. Transfers count by direction, so a transfer moves money between accounts.
  // Accounts without transactions have no row; callers treat that as 0. The transactions table
  // is in the same database but its feature package is separate, hence native SQL here.
  @Query(
      value =
          """
          SELECT account_id AS "accountId",
                 SUM(CASE WHEN type IN ('income', 'transfer_in')
                          THEN amount_paise ELSE -amount_paise END)::bigint AS "netPaise"
          FROM transactions
          WHERE workspace_id = :workspaceId AND deleted_at IS NULL
          GROUP BY account_id
          """,
      nativeQuery = true)
  List<NetFlow> netFlowsByWorkspace(@Param("workspaceId") UUID workspaceId);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(CASE WHEN type IN ('income', 'transfer_in')
                                   THEN amount_paise ELSE -amount_paise END), 0)::bigint
          FROM transactions
          WHERE account_id = :accountId AND deleted_at IS NULL
          """,
      nativeQuery = true)
  long netFlowForAccount(@Param("accountId") UUID accountId);
}
