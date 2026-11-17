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
        .contains("workspaces", "workspace_members", "accounts", "categories");
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
