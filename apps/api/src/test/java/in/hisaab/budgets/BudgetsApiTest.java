package in.hisaab.budgets;

import static in.hisaab.TestFixtures.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class BudgetsApiTest extends PostgresTestBase {

  // New users default to Asia/Kolkata with period start day 1.
  private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void spendRollsUpSubcategoriesAndIgnoresIncomeTransfersAndDeleted() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = categoryId(s, ws, "Food", "expense");
    String groceries = childId(s, ws, food);
    String cash = cashId(s, ws);

    setBudget(s, ws, food, 500000).andExpect(status().isNoContent());
    postTx(s, ws, tx(cash, "expense", 10000, food));
    postTx(s, ws, tx(cash, "expense", 25000, groceries));
    // Income has no categories, so it is posted without one.
    postTx(
        s,
        ws,
        "{\"account_id\": \"%s\", \"type\": \"income\", \"amount_paise\": 99999, \"occurred_on\": \"%s\"}"
            .formatted(cash, TODAY));
    String deleted = JsonPath.read(postTx(s, ws, tx(cash, "expense", 7777, food)), "$.id");
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/workspaces/" + ws + "/transactions/" + deleted)
                .cookie(auth(s)))
        .andExpect(status().isNoContent());

    list(s, ws, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].category_name").value("Food"))
        .andExpect(jsonPath("$.items[0].amount_paise").value(500000))
        .andExpect(jsonPath("$.items[0].spent_paise").value(35000));
  }

  @Test
  void settingAgainReplacesAndZeroRemoves() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = categoryId(s, ws, "Food", "expense");

    setBudget(s, ws, food, 100).andExpect(status().isNoContent());
    setBudget(s, ws, food, 200).andExpect(status().isNoContent());
    list(s, ws, "").andExpect(jsonPath("$.items[0].amount_paise").value(200));

    setBudget(s, ws, food, 0).andExpect(status().isNoContent());
    list(s, ws, "").andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void earlierPeriodsAreUnaffectedByACurrentChange() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = categoryId(s, ws, "Food", "expense");
    setBudget(s, ws, food, 100).andExpect(status().isNoContent());

    String lastMonth = LocalDate.parse(TODAY).withDayOfMonth(1).minusDays(1).toString();
    list(s, ws, "?period=" + lastMonth).andExpect(jsonPath("$.items.length()").value(0));
    String nextMonth = LocalDate.parse(TODAY).withDayOfMonth(1).plusMonths(1).toString();
    // The budget carries forward to later periods.
    list(s, ws, "?period=" + nextMonth).andExpect(jsonPath("$.items[0].amount_paise").value(100));
  }

  @Test
  void onlyTopLevelCategoriesCanBeBudgeted() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String food = categoryId(s, ws, "Food", "expense");

    setBudget(s, ws, childId(s, ws, food), 100).andExpect(status().isUnprocessableContent());
    setBudget(s, ws, food, -1).andExpect(status().isBadRequest());
  }

  @Test
  void otherWorkspacesAreNotFound() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    String food = categoryId(a, wsA, "Food", "expense");

    list(b, wsA, "").andExpect(status().isNotFound());
    setBudget(b, wsA, food, 100).andExpect(status().isNotFound());
  }

  private ResultActions list(Session s, UUID ws, String query) throws Exception {
    return mockMvc.perform(get("/api/v1/workspaces/" + ws + "/budgets" + query).cookie(auth(s)));
  }

  private ResultActions setBudget(Session s, UUID ws, String categoryId, long paise)
      throws Exception {
    return mockMvc.perform(
        put("/api/v1/workspaces/" + ws + "/budgets/" + categoryId)
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount_paise\": " + paise + "}"));
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

  private static String tx(String accountId, String type, long paise, String categoryId) {
    return ("{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": %d,"
            + " \"occurred_on\": \"%s\", \"category_id\": \"%s\"}")
        .formatted(accountId, type, paise, TODAY, categoryId);
  }

  private String cashId(Session s, UUID ws) throws Exception {
    List<String> ids =
        JsonPath.read(categoriesOrAccounts(s, ws, "accounts"), "$.items[?(@.name == 'Cash')].id");
    return ids.getFirst();
  }

  private String categoryId(Session s, UUID ws, String name, String kind) throws Exception {
    List<String> ids =
        JsonPath.read(
            categoriesOrAccounts(s, ws, "categories"),
            "$.items[?(@.name == '%s' && @.kind == '%s')].id".formatted(name, kind));
    return ids.getFirst();
  }

  /** Defaults have no subcategories, so this creates a "Groceries" child under the parent. */
  private String childId(Session s, UUID ws, String parentId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + "/categories")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"Groceries\", \"kind\": \"expense\", \"parent_id\": \"%s\"}"
                            .formatted(parentId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String categoriesOrAccounts(Session s, UUID ws, String path) throws Exception {
    return mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/" + path).cookie(auth(s)))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
