package in.hisaab.categories;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs V14's SQL against old-style data. Flyway already applied it to the empty test database, so
 * this seeds what an older workspace had, runs the file again as the table owner (as Flyway does)
 * and checks the result through the API. It also guards the row-level-security trap: the owner is
 * subject to FORCE RLS, so a migration that forgot to lift it would change nothing.
 */
class ExpenseOnlyMigrationTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Value("${DB_URL}")
  private String dbUrl;

  @Test
  void removesIncomeAndNamedDefaultsButKeepsCustomCategories() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    UUID housing = UUID.randomUUID();
    UUID rent = UUID.randomUUID();
    UUID mortgage = UUID.randomUUID();
    UUID salary = UUID.randomUUID();

    try (Connection owner =
        DriverManager.getConnection(dbUrl, "hisaab_owner", "hisaab_dev_owner")) {
      owner.setAutoCommit(false);
      try (var st = owner.createStatement()) {
        st.execute("ALTER TABLE categories NO FORCE ROW LEVEL SECURITY");
        // An old workspace: Housing (default) with Rent (default) and Mortgage (custom), plus
        // Salary.
        seed(owner, ws, housing, null, "Housing", "expense", true);
        seed(owner, ws, rent, housing, "Rent", "expense", true);
        seed(owner, ws, mortgage, housing, "Mortgage", "expense", false);
        seed(owner, ws, salary, null, "Salary", "income", true);
        st.execute("ALTER TABLE categories FORCE ROW LEVEL SECURITY");
      }
      owner.commit();

      // A transaction on a category that is about to be deleted.
      mockMvc
          .perform(
              post("/api/v1/workspaces/" + ws + "/transactions")
                  .cookie(auth(s))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      ("{\"account_id\": \"%s\", \"type\": \"expense\", \"amount_paise\": 100,"
                              + " \"occurred_on\": \"2026-11-01\", \"category_id\": \"%s\"}")
                          .formatted(cashId(s, ws), rent)))
          .andExpect(status().isCreated());

      String migration =
          new String(
              new ClassPathResource("db/migration/V14__expense_only_categories.sql")
                  .getInputStream()
                  .readAllBytes());
      try (var st = owner.createStatement()) {
        st.execute(migration);
      }
      owner.commit();
    }

    String body =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/categories").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> names = JsonPath.read(body, "$.items[*].name");
    assertThat(names).doesNotContain("Housing", "Rent", "Salary").contains("Mortgage", "Food");
    // The custom subcategory of the deleted parent moved to the top level.
    List<Object> mortgageParent = JsonPath.read(body, "$.items[?(@.name == 'Mortgage')].parent_id");
    assertThat(mortgageParent).containsExactly((Object) null);
    // The transaction stays, now uncategorized.
    mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/transactions").cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].category_id").isEmpty());
  }

  private static void seed(
      Connection owner,
      UUID workspaceId,
      UUID id,
      UUID parentId,
      String name,
      String kind,
      boolean system)
      throws Exception {
    try (var ps =
        owner.prepareStatement(
            "INSERT INTO categories (id, workspace_id, parent_id, name, kind, is_system, sort_order)"
                + " VALUES (?, ?, ?, ?, ?, ?, 0)")) {
      ps.setObject(1, id);
      ps.setObject(2, workspaceId);
      ps.setObject(3, parentId);
      ps.setString(4, name);
      ps.setString(5, kind);
      ps.setBoolean(6, system);
      ps.executeUpdate();
    }
  }

  private String cashId(Session s, UUID ws) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(body, "$.items[?(@.name == 'Cash')].id");
    return ids.getFirst();
  }
}
