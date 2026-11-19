package in.hisaab.reports;

import static in.hisaab.TestFixtures.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class ReportsApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void totalsReconcileAndIgnoreTransfersDeletedAndOtherPeriods() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = accountId(s, ws, "Cash");
    String food = categoryId(s, ws, "Food", "expense");
    String groceries = childId(s, ws, food);

    postTx(s, ws, tx(cash, "income", 100000, "2024-02-01", categoryId(s, ws, "Salary", "income")));
    postTx(s, ws, tx(cash, "expense", 10000, "2024-02-02", food));
    postTx(s, ws, tx(cash, "expense", 25000, "2024-02-02", groceries));
    postTx(s, ws, tx(cash, "expense", 5000, "2024-02-29", null));
    // Outside February: January belongs to "previous", March to nothing we assert on.
    postTx(s, ws, tx(cash, "expense", 40000, "2024-01-31", food));
    postTx(s, ws, tx(cash, "expense", 999, "2024-03-01", food));
    // Soft-deleted expense and a transfer must not count anywhere.
    String deleted =
        JsonPath.read(postTx(s, ws, tx(cash, "expense", 7777, "2024-02-10", food)), "$.id");
    mockMvc
        .perform(delete("/api/v1/workspaces/" + ws + "/transactions/" + deleted).cookie(auth(s)))
        .andExpect(status().isNoContent());
    transfer(s, ws, cash, bankId(s, ws), 77777, "2024-02-10");

    monthly(s, ws, "?period=2024-02-15")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period_start").value("2024-02-01"))
        .andExpect(jsonPath("$.period_end").value("2024-02-29"))
        .andExpect(jsonPath("$.totals.income_paise").value(100000))
        .andExpect(jsonPath("$.totals.expense_paise").value(40000))
        .andExpect(jsonPath("$.totals.net_paise").value(60000))
        .andExpect(jsonPath("$.previous.expense_paise").value(40000))
        .andExpect(jsonPath("$.previous.income_paise").value(0))
        .andExpect(jsonPath("$.previous.net_paise").value(-40000))
        // Children roll up into Food; the category-less expense gets its own row.
        .andExpect(jsonPath("$.by_category.length()").value(2))
        .andExpect(jsonPath("$.by_category[0].category_name").value("Food"))
        .andExpect(jsonPath("$.by_category[0].spent_paise").value(35000))
        .andExpect(jsonPath("$.by_category[1].category_name").value("Uncategorized"))
        .andExpect(jsonPath("$.by_category[1].spent_paise").value(5000))
        // Leap February: 29 days, zero-filled.
        .andExpect(jsonPath("$.daily.length()").value(29))
        .andExpect(jsonPath("$.daily[0].spent_paise").value(0))
        .andExpect(jsonPath("$.daily[1].spent_paise").value(35000))
        .andExpect(jsonPath("$.daily[28].day").value("2024-02-29"))
        .andExpect(jsonPath("$.daily[28].spent_paise").value(5000))
        .andExpect(jsonPath("$.top_expenses.length()").value(3))
        .andExpect(jsonPath("$.top_expenses[0].amount_paise").value(25000));
  }

  @Test
  void nonLeapFebruaryHasTwentyEightDays() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    monthly(s, ws, "?period=2025-02-10")
        .andExpect(jsonPath("$.period_end").value("2025-02-28"))
        .andExpect(jsonPath("$.daily.length()").value(28))
        .andExpect(jsonPath("$.totals.net_paise").value(0))
        .andExpect(jsonPath("$.top_expenses.length()").value(0));
  }

  @Test
  void customPeriodStartDayMovesTheBoundary() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = accountId(s, ws, "Cash");
    String food = categoryId(s, ws, "Food", "expense");
    mockMvc
        .perform(
            patch("/api/v1/me")
                .cookie(auth(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"period_start_day\": 15}"))
        .andExpect(status().isOk());
    postTx(s, ws, tx(cash, "expense", 100, "2024-03-14", food));
    postTx(s, ws, tx(cash, "expense", 200, "2024-03-15", food));

    monthly(s, ws, "?period=2024-03-14")
        .andExpect(jsonPath("$.period_start").value("2024-02-15"))
        .andExpect(jsonPath("$.period_end").value("2024-03-14"))
        .andExpect(jsonPath("$.totals.expense_paise").value(100));
    monthly(s, ws, "?period=2024-03-15")
        .andExpect(jsonPath("$.period_start").value("2024-03-15"))
        .andExpect(jsonPath("$.totals.expense_paise").value(200))
        .andExpect(jsonPath("$.previous.expense_paise").value(100));
  }

  @Test
  void topExpensesAreCappedAtFive() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = accountId(s, ws, "Cash");
    for (int i = 1; i <= 6; i++) {
      postTx(s, ws, tx(cash, "expense", i * 100, "2024-05-10", null));
    }
    monthly(s, ws, "?period=2024-05-10")
        .andExpect(jsonPath("$.top_expenses.length()").value(5))
        .andExpect(jsonPath("$.top_expenses[0].amount_paise").value(600))
        .andExpect(jsonPath("$.top_expenses[4].amount_paise").value(200));
  }

  @Test
  void otherWorkspacesAreNotFound() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    monthly(b, fixtures.workspaceIdOf(a), "").andExpect(status().isNotFound());
  }

  private ResultActions monthly(Session s, UUID ws, String query) throws Exception {
    return mockMvc.perform(
        get("/api/v1/workspaces/" + ws + "/reports/monthly" + query).cookie(auth(s)));
  }

  private String postTx(Session s, UUID ws, String json) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/workspaces/" + ws + "/transactions")
                .cookie(auth(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void transfer(Session s, UUID ws, String from, String to, long paise, String day)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspaces/" + ws + "/transfers")
                .cookie(auth(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"from_account_id\": \"%s\", \"to_account_id\": \"%s\","
                            + " \"amount_paise\": %d, \"occurred_on\": \"%s\"}")
                        .formatted(from, to, paise, day)))
        .andExpect(status().isCreated());
  }

  private static String tx(
      String accountId, String type, long paise, String day, String categoryId) {
    String category = categoryId == null ? "" : ", \"category_id\": \"" + categoryId + "\"";
    return ("{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": %d,"
            + " \"occurred_on\": \"%s\"%s}")
        .formatted(accountId, type, paise, day, category);
  }

  private String bankId(Session s, UUID ws) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + "/accounts")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Bank\", \"type\": \"bank\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private String accountId(Session s, UUID ws, String name) throws Exception {
    List<String> ids =
        JsonPath.read(fetch(s, ws, "accounts"), "$.items[?(@.name == '%s')].id".formatted(name));
    return ids.getFirst();
  }

  private String categoryId(Session s, UUID ws, String name, String kind) throws Exception {
    List<String> ids =
        JsonPath.read(
            fetch(s, ws, "categories"),
            "$.items[?(@.name == '%s' && @.kind == '%s')].id".formatted(name, kind));
    return ids.getFirst();
  }

  private String childId(Session s, UUID ws, String parentId) throws Exception {
    List<String> ids =
        JsonPath.read(
            fetch(s, ws, "categories"), "$.items[?(@.parent_id == '%s')].id".formatted(parentId));
    return ids.getFirst();
  }

  private String fetch(Session s, UUID ws, String path) throws Exception {
    return mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/" + path).cookie(auth(s)))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
