package in.hisaab.accounts;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AccountsApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void newUserHasOneCashAccountWithZeroBalance() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    mockMvc
        .perform(get(url(ws)).cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Cash"))
        .andExpect(jsonPath("$.items[0].type").value("cash"))
        .andExpect(jsonPath("$.items[0].balance_paise").value(0))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void createReturns201AndBalanceEqualsOpeningBalance() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postAcct(
            s,
            ws,
            """
            {"name": "  HDFC Savings ", "type": "bank", "opening_balance_paise": 500000}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("HDFC Savings"))
        .andExpect(jsonPath("$.type").value("bank"))
        .andExpect(jsonPath("$.opening_balance_paise").value(500000))
        .andExpect(jsonPath("$.balance_paise").value(500000))
        .andExpect(jsonPath("$.archived_at").doesNotExist());
  }

  @Test
  void balanceIsOpeningPlusIncomeMinusExpenseAndIgnoresDeletedTransactions() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String bank =
        JsonPath.read(
            postAcct(
                    s,
                    ws,
                    "{\"name\": \"Bank\", \"type\": \"bank\", \"opening_balance_paise\": 100000}")
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    postTxn(s, ws, bank, "income", 50000);
    postTxn(s, ws, bank, "expense", 12000);
    String gone = postTxn(s, ws, bank, "expense", 999);
    mockMvc
        .perform(delete("/api/v1/workspaces/" + ws + "/transactions/" + gone).cookie(auth(s)))
        .andExpect(status().isNoContent());

    // 100000 + 50000 - 12000; the deleted 999 does not count. Cash has no transactions.
    mockMvc
        .perform(get(url(ws)).cookie(auth(s)))
        .andExpect(jsonPath("$.items[?(@.name == 'Bank')].balance_paise").value(138000))
        .andExpect(jsonPath("$.items[?(@.name == 'Bank')].opening_balance_paise").value(100000))
        .andExpect(jsonPath("$.items[?(@.name == 'Cash')].balance_paise").value(0));
    // PATCH reports the same computed balance.
    patchAcct(s, ws, bank, "{\"name\": \"Bank 2\"}")
        .andExpect(jsonPath("$.balance_paise").value(138000));

    // Restoring brings it back into the balance.
    mockMvc
        .perform(
            post("/api/v1/workspaces/" + ws + "/transactions/" + gone + "/restore").cookie(auth(s)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(url(ws)).cookie(auth(s)))
        .andExpect(jsonPath("$.items[?(@.name == 'Bank 2')].balance_paise").value(137001));
  }

  @Test
  void openingBalanceCanBeNegativeOrVeryLarge() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postAcct(
            s,
            ws,
            "{\"name\": \"Card\", \"type\": \"credit_card\", \"opening_balance_paise\": -125000}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("credit_card"))
        .andExpect(jsonPath("$.balance_paise").value(-125000));
    postAcct(
            s,
            ws,
            "{\"name\": \"Big\", \"type\": \"bank\", \"opening_balance_paise\": 9000000000000000000}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balance_paise").value(9000000000000000000L));
  }

  @Test
  void openingBalanceDefaultsToZero() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postAcct(s, ws, "{\"name\": \"Paytm\", \"type\": \"wallet\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.opening_balance_paise").value(0));
  }

  @Test
  void patchRenamesArchivesAndUnarchives() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String id = createBank(s, ws, "Old name");

    patchAcct(s, ws, id, "{\"name\": \"New name\", \"opening_balance_paise\": 1000}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New name"))
        .andExpect(jsonPath("$.balance_paise").value(1000))
        .andExpect(jsonPath("$.type").value("bank"));
    patchAcct(s, ws, id, "{\"archived\": true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived_at").exists())
        .andExpect(jsonPath("$.name").value("New name"));
    patchAcct(s, ws, id, "{\"archived\": false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived_at").doesNotExist());
  }

  @Test
  void blankNameMissingTypeAndUnknownTypeAreRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postAcct(s, ws, "{\"name\": \"  \", \"type\": \"bank\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("validation_failed"))
        .andExpect(jsonPath("$.error.details.fields.name").exists());
    postAcct(s, ws, "{\"name\": \"X\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields.type").exists());
    postAcct(s, ws, "{\"name\": \"X\", \"type\": \"piggy_bank\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void blankNameInPatchIsRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String id = createBank(s, ws, "Savings");

    patchAcct(s, ws, id, "{\"name\": \"   \"}").andExpect(status().isBadRequest());
  }

  @Test
  void duplicateNameIgnoringCaseGets409() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    // "Cash" already exists from signup.
    postAcct(s, ws, "{\"name\": \"CASH\", \"type\": \"wallet\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("name_taken"));

    String id = createBank(s, ws, "Savings");
    patchAcct(s, ws, id, "{\"name\": \"cash\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("name_taken"));
  }

  @Test
  void sameNameIsAllowedInDifferentWorkspaces() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();

    postAcct(a, fixtures.workspaceIdOf(a), "{\"name\": \"Savings\", \"type\": \"bank\"}")
        .andExpect(status().isCreated());
    postAcct(b, fixtures.workspaceIdOf(b), "{\"name\": \"Savings\", \"type\": \"bank\"}")
        .andExpect(status().isCreated());
  }

  @Test
  void nonMemberGets404OnListCreateAndPatch() throws Exception {
    Session owner = fixtures.signup();
    UUID ownersWs = fixtures.workspaceIdOf(owner);
    String ownersAccount = createBank(owner, ownersWs, "Private");
    Session intruder = fixtures.signup();

    mockMvc.perform(get(url(ownersWs)).cookie(auth(intruder))).andExpect(status().isNotFound());
    postAcct(intruder, ownersWs, "{\"name\": \"Sneaky\", \"type\": \"bank\"}")
        .andExpect(status().isNotFound());
    patchAcct(intruder, ownersWs, ownersAccount, "{\"name\": \"Hacked\"}")
        .andExpect(status().isNotFound());

    // The owner's data is untouched.
    mockMvc
        .perform(get(url(ownersWs)).cookie(auth(owner)))
        .andExpect(jsonPath("$.items[?(@.name == 'Hacked')]").isEmpty())
        .andExpect(jsonPath("$.items[?(@.name == 'Sneaky')]").isEmpty());
  }

  @Test
  void accountIdFromAnotherWorkspaceGets404() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    String bsAccount = createBank(b, fixtures.workspaceIdOf(b), "B's account");

    // A uses their own workspace URL but B's account id.
    patchAcct(a, fixtures.workspaceIdOf(a), bsAccount, "{\"name\": \"Mine now\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void requestsWithoutATokenGet401() throws Exception {
    mockMvc.perform(get(url(UUID.randomUUID()))).andExpect(status().isUnauthorized());
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/accounts";
  }

  private ResultActions postAcct(Session s, UUID ws, String json) throws Exception {
    return mockMvc.perform(
        post(url(ws)).cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions patchAcct(Session s, UUID ws, String accountId, String json)
      throws Exception {
    return mockMvc.perform(
        patch(url(ws) + "/" + accountId)
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private String postTxn(Session s, UUID ws, String accountId, String type, long amount)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + "/transactions")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": %d,"
                                .formatted(accountId, type, amount)
                            + " \"occurred_on\": \"2026-11-01\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String createBank(Session s, UUID ws, String name) throws Exception {
    String body =
        postAcct(s, ws, "{\"name\": \"%s\", \"type\": \"bank\"}".formatted(name))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }
}
