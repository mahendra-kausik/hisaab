package in.hisaab.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.TestUser;
import in.hisaab.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves Postgres itself keeps users apart. These tests run raw SQL, skipping WorkspaceAccess, so
 * they show what RLS blocks even if application code forgets its own check.
 */
class RlsIsolationTest extends PostgresTestBase {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;
  @Autowired private CurrentUser currentUser;
  @Autowired private TestFixtures fixtures;

  /** Runs the SQL in a transaction as the given user, like a real service method would. */
  private <T> T asUser(UUID userId, Supplier<T> work) {
    return currentUser.runAs(userId, () -> tx.execute(status -> work.get()));
  }

  private <T> T asNobody(Supplier<T> work) {
    return tx.execute(status -> work.get());
  }

  @Test
  void eachUserSeesOnlyTheirOwnWorkspaceAndMembership() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThat(asUser(a.id(), () -> jdbc.queryForList("SELECT id FROM workspaces", UUID.class)))
        .containsExactly(a.workspaceId());
    assertThat(
            asUser(
                b.id(),
                () -> jdbc.queryForList("SELECT workspace_id FROM workspace_members", UUID.class)))
        .containsExactly(b.workspaceId());
  }

  @Test
  void userCannotSelectAnotherUsersWorkspaceById() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    Integer visible =
        asUser(
            a.id(),
            () ->
                jdbc.queryForObject(
                    "SELECT count(*) FROM workspaces WHERE id = ?",
                    Integer.class,
                    b.workspaceId()));
    assertThat(visible).isZero();
  }

  @Test
  void noCurrentUserSeesNoRows() {
    fixtures.createUser();

    assertThat(
            asNobody(() -> jdbc.queryForObject("SELECT count(*) FROM workspaces", Integer.class)))
        .isZero();
    assertThat(
            asNobody(
                () -> jdbc.queryForObject("SELECT count(*) FROM workspace_members", Integer.class)))
        .isZero();
  }

  @Test
  void userCannotAddThemselvesToAnotherUsersWorkspace() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO workspace_members (workspace_id, user_id, role)"
                            + " VALUES (?, ?, 'owner')",
                        b.workspaceId(),
                        a.id())))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void userCannotAddSomeoneElseToTheirOwnWorkspace() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO workspace_members (workspace_id, user_id, role)"
                            + " VALUES (?, ?, 'owner')",
                        a.workspaceId(),
                        b.id())))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void userCannotCreateAWorkspaceOnBehalfOfAnotherUser() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO workspaces (name, kind, created_by)"
                            + " VALUES ('Sneaky', 'personal', ?)",
                        b.id())))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  // Finds tables by the workspace_id column, so a future workspace-owned table that forgets RLS
  // fails here without anyone having to remember to add it to this test.
  @Test
  void rlsIsEnabledAndForcedOnEveryWorkspaceOwnedTable() {
    List<Map<String, Object>> tables =
        jdbc.queryForList(
            "SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class"
                + " WHERE relkind = 'r' AND relnamespace = 'public'::regnamespace"
                + " AND (relname = 'workspaces' OR relname IN ("
                + "   SELECT table_name FROM information_schema.columns"
                + "   WHERE table_schema = 'public' AND column_name = 'workspace_id'))");

    assertThat(tables)
        .extracting(t -> t.get("relname"))
        .contains(
            "workspaces",
            "workspace_members",
            "accounts",
            "categories",
            "transactions",
            "tags",
            "transaction_tags",
            "budgets",
            "recurring_rules",
            "parties",
            "projects",
            "dues");
    assertThat(tables)
        .allSatisfy(t ->
            assertThat(t)
                .containsEntry("relrowsecurity", true)
                .containsEntry("relforcerowsecurity", true));
  }

  @Test
  void eachUserSeesOnlyTheirOwnAccountsAndCategories() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThat(
            asUser(
                a.id(), () -> jdbc.queryForList("SELECT workspace_id FROM accounts", UUID.class)))
        .isNotEmpty()
        .containsOnly(a.workspaceId());
    assertThat(
            asUser(
                b.id(), () -> jdbc.queryForList("SELECT workspace_id FROM categories", UUID.class)))
        .isNotEmpty()
        .containsOnly(b.workspaceId());
    assertThat(asNobody(() -> jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)))
        .isZero();
    assertThat(
            asNobody(() -> jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class)))
        .isZero();
  }

  @Test
  void userCannotInsertAnAccountIntoAnotherUsersWorkspace() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO accounts (workspace_id, name, type) VALUES (?, 'Sneaky',"
                            + " 'bank')",
                        b.workspaceId())))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void userCannotInsertACategoryIntoAnotherUsersWorkspace() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO categories (workspace_id, name, kind, sort_order)"
                            + " VALUES (?, 'Sneaky', 'expense', 0)",
                        b.workspaceId())))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void userCannotUpdateOrDeleteAnotherUsersAccount() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    // RLS hides B's rows from A, so these statements match nothing instead of failing.
    assertThat(
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "UPDATE accounts SET name = 'Hacked' WHERE workspace_id = ?",
                        b.workspaceId())))
        .isZero();
    assertThat(
            asUser(
                a.id(),
                () -> jdbc.update("DELETE FROM accounts WHERE workspace_id = ?", b.workspaceId())))
        .isZero();
    assertThat(
            asUser(
                b.id(),
                () ->
                    jdbc.queryForObject(
                        "SELECT count(*) FROM accounts WHERE name = 'Cash'", Integer.class)))
        .isEqualTo(1);
  }

  @Test
  void aCategoryParentMustBeInTheSameWorkspaceAndKind() {
    TestUser a = fixtures.createUser();
    UUID parentId =
        asUser(
            a.id(),
            () ->
                jdbc.queryForObject(
                    "SELECT id FROM categories WHERE workspace_id = ? AND name = 'Food'",
                    UUID.class,
                    a.workspaceId()));

    // Right workspace, wrong kind: the composite foreign key rejects it.
    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO categories (workspace_id, parent_id, name, kind, sort_order)"
                            + " VALUES (?, ?, 'Tips', 'income', 0)",
                        a.workspaceId(),
                        parentId)))
        .rootCause()
        .hasMessageContaining("foreign key");
  }

  private UUID accountOf(TestUser u) {
    return asUser(
        u.id(),
        () ->
            jdbc.queryForObject(
                "SELECT id FROM accounts WHERE workspace_id = ? AND name = 'Cash'",
                UUID.class,
                u.workspaceId()));
  }

  private int insertTransaction(TestUser as, UUID workspaceId, UUID accountId) {
    return asUser(
        as.id(),
        () ->
            jdbc.update(
                "INSERT INTO transactions (workspace_id, account_id, type, amount_paise,"
                    + " occurred_on, created_by) VALUES (?, ?, 'expense', 100, '2026-11-01', ?)",
                workspaceId,
                accountId,
                as.id()));
  }

  @Test
  void eachUserSeesOnlyTheirOwnTransactionsAndTags() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();
    insertTransaction(a, a.workspaceId(), accountOf(a));
    insertTransaction(b, b.workspaceId(), accountOf(b));
    asUser(
        a.id(),
        () ->
            jdbc.update(
                "INSERT INTO tags (workspace_id, name) VALUES (?, 'mine')", a.workspaceId()));

    assertThat(
            asUser(
                a.id(),
                () -> jdbc.queryForList("SELECT workspace_id FROM transactions", UUID.class)))
        .containsExactly(a.workspaceId());
    assertThat(asUser(b.id(), () -> jdbc.queryForList("SELECT id FROM tags", UUID.class)))
        .isEmpty();
    for (String table : List.of("transactions", "tags", "transaction_tags")) {
      assertThat(
              asNobody(() -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)))
          .isZero();
    }
  }

  @Test
  void userCannotInsertATransactionIntoAnotherUsersWorkspace() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() -> insertTransaction(a, b.workspaceId(), accountOf(b)))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void userCannotUpdateOrDeleteAnotherUsersTransaction() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();
    insertTransaction(b, b.workspaceId(), accountOf(b));

    assertThat(
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "UPDATE transactions SET amount_paise = 1 WHERE workspace_id = ?",
                        b.workspaceId())))
        .isZero();
    assertThat(
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "DELETE FROM transactions WHERE workspace_id = ?", b.workspaceId())))
        .isZero();
  }

  // Foreign key checks ignore RLS, so without the composite (account_id, workspace_id) key a
  // user could attach their own transaction to someone else's account id.
  @Test
  void aTransactionCannotPointAtAnotherWorkspacesAccountOrCategory() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() -> insertTransaction(a, a.workspaceId(), accountOf(b)))
        .rootCause()
        .hasMessageContaining("foreign key");

    UUID bsCategory =
        asUser(
            b.id(),
            () ->
                jdbc.queryForObject(
                    "SELECT id FROM categories WHERE workspace_id = ? AND name = 'Food'",
                    UUID.class,
                    b.workspaceId()));
    UUID asAccount = accountOf(a);
    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO transactions (workspace_id, account_id, type, amount_paise,"
                            + " occurred_on, category_id, created_by)"
                            + " VALUES (?, ?, 'expense', 100, '2026-11-01', ?, ?)",
                        a.workspaceId(),
                        asAccount,
                        bsCategory,
                        a.id())))
        .rootCause()
        .hasMessageContaining("foreign key");
  }

  @Test
  void aTagLinkCannotJoinATransactionToAnotherWorkspacesTag() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();
    insertTransaction(a, a.workspaceId(), accountOf(a));
    UUID asTransaction =
        asUser(
            a.id(),
            () ->
                jdbc.queryForObject(
                    "SELECT id FROM transactions WHERE workspace_id = ?",
                    UUID.class,
                    a.workspaceId()));
    UUID bsTag =
        asUser(
            b.id(),
            () ->
                jdbc.queryForObject(
                    "INSERT INTO tags (workspace_id, name) VALUES (?, 'b-tag') RETURNING id",
                    UUID.class,
                    b.workspaceId()));

    assertThatThrownBy(() ->
            asUser(
                a.id(),
                () ->
                    jdbc.update(
                        "INSERT INTO transaction_tags (workspace_id, transaction_id, tag_id)"
                            + " VALUES (?, ?, ?)",
                        a.workspaceId(),
                        asTransaction,
                        bsTag)))
        .rootCause()
        .hasMessageContaining("foreign key");
  }

  @Test
  void currentUserSettingDoesNotLeakIntoTheNextTransaction() {
    TestUser a = fixtures.createUser();

    UUID during =
        asUser(a.id(), () -> jdbc.queryForObject("SELECT app_current_user_id()", UUID.class));
    UUID after = asNobody(() -> jdbc.queryForObject("SELECT app_current_user_id()", UUID.class));

    assertThat(during).isEqualTo(a.id());
    assertThat(after).isNull();
  }
}
