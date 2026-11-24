package in.hisaab.transactions;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TransactionsApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  // ---- create, read, edit ----

  @Test
  void createReturns201AndCanBeReadBackAndListed() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String food = categoryId(s, ws, "Food", "expense");

    String body =
        postTx(
                s,
                ws,
                tx(cash, "expense", 12550, "2026-11-01")
                    .replace(
                        "}",
                        """
                        , "category_id": "%s", "payment_method": "upi",
                          "note": "  Lunch  ", "tags": ["Rent", "food"]}
                        """
                            .formatted(food)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account_id").value(cash))
            .andExpect(jsonPath("$.type").value("expense"))
            .andExpect(jsonPath("$.amount_paise").value(12550))
            .andExpect(jsonPath("$.occurred_on").value("2026-11-01"))
            .andExpect(jsonPath("$.category_id").value(food))
            .andExpect(jsonPath("$.payment_method").value("upi"))
            .andExpect(jsonPath("$.note").value("Lunch"))
            .andExpect(jsonPath("$.source").value("manual"))
            .andExpect(jsonPath("$.created_at").exists())
            // Tags come back sorted by name, ignoring case.
            .andExpect(jsonPath("$.tags[0]").value("food"))
            .andExpect(jsonPath("$.tags[1]").value("Rent"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");

    mockMvc
        .perform(get(url(ws) + "/" + id).cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.amount_paise").value(12550));
    listTx(s, ws)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void minimalCreateLeavesOptionalFieldsEmpty() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postTx(s, ws, tx(cashId(s, ws), "income", 100, "2026-11-01"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.category_id").doesNotExist())
        .andExpect(jsonPath("$.payment_method").doesNotExist())
        .andExpect(jsonPath("$.note").doesNotExist())
        .andExpect(jsonPath("$.tags.length()").value(0));
  }

  @Test
  void putReplacesEverythingAndClearsWhatIsLeftOut() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String food = categoryId(s, ws, "Food", "expense");
    String id =
        createTx(
            s,
            ws,
            tx(cash, "expense", 500, "2026-11-01")
                .replace(
                    "}",
                    ", \"category_id\": \"%s\", \"note\": \"old\", \"payment_method\": \"cash\","
                            .formatted(food)
                        + " \"tags\": [\"a\", \"b\"]}"));

    putTx(s, ws, id, tx(cash, "income", 900, "2026-11-02").replace("}", ", \"tags\": [\"b\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("income"))
        .andExpect(jsonPath("$.amount_paise").value(900))
        .andExpect(jsonPath("$.occurred_on").value("2026-11-02"))
        .andExpect(jsonPath("$.category_id").doesNotExist())
        .andExpect(jsonPath("$.note").doesNotExist())
        .andExpect(jsonPath("$.payment_method").doesNotExist())
        .andExpect(jsonPath("$.tags.length()").value(1))
        .andExpect(jsonPath("$.tags[0]").value("b"));
  }

  // ---- delete and restore ----

  @Test
  void deleteHidesFromListAndRestoreBringsItBack() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String id = createTx(s, ws, tx(cashId(s, ws), "expense", 700, "2026-11-01"));

    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s))).andExpect(status().isNoContent());
    listTx(s, ws).andExpect(jsonPath("$.items.length()").value(0));
    mockMvc.perform(get(url(ws) + "/" + id).cookie(auth(s))).andExpect(status().isNotFound());
    // Deleting twice: it is already gone.
    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s))).andExpect(status().isNotFound());

    mockMvc
        .perform(post(url(ws) + "/" + id + "/restore").cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    listTx(s, ws).andExpect(jsonPath("$.items.length()").value(1));
    // Restoring one that is not deleted is harmless.
    mockMvc
        .perform(post(url(ws) + "/" + id + "/restore").cookie(auth(s)))
        .andExpect(status().isOk());
  }

  @Test
  void deletedTransactionCannotBeEditedOrFound() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String id = createTx(s, ws, tx(cash, "expense", 700, "2026-11-01"));
    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s)));

    putTx(s, ws, id, tx(cash, "expense", 1, "2026-11-01")).andExpect(status().isNotFound());
  }

  @Test
  void restoreOfAnUnknownIdGets404() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    mockMvc
        .perform(post(url(ws) + "/" + UUID.randomUUID() + "/restore").cookie(auth(s)))
        .andExpect(status().isNotFound());
  }

  // ---- validation ----

  @Test
  void invalidBodiesGet400() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    postTx(
            s,
            ws,
            "{\"account_id\": \"%s\", \"type\": \"expense\", \"occurred_on\": \"2026-11-01\"}"
                .formatted(cash))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("validation_failed"))
        .andExpect(jsonPath("$.error.details.fields.amount_paise").exists());
    postTx(s, ws, tx(cash, "expense", 0, "2026-11-01")).andExpect(status().isBadRequest());
    postTx(s, ws, tx(cash, "expense", -5, "2026-11-01")).andExpect(status().isBadRequest());
    postTx(s, ws, tx(cash, "expense", 5, "01-11-2026")).andExpect(status().isBadRequest());
    postTx(s, ws, tx(cash, "refund", 5, "2026-11-01")).andExpect(status().isBadRequest());
    postTx(s, ws, tx(cash, "expense", 5, "2026-11-01").replace("}", ", \"payment_method\": \"x\"}"))
        .andExpect(status().isBadRequest());
    postTx(s, ws, tx(cash, "expense", 5, "2026-11-01").replace("}", ", \"tags\": [\"  \"]}"))
        .andExpect(status().isBadRequest());
    postTx(
            s,
            ws,
            tx(cash, "expense", 5, "2026-11-01")
                .replace("}", ", \"note\": \"%s\"}".formatted("x".repeat(501))))
        .andExpect(status().isBadRequest());
    postTx(
            s,
            ws,
            tx(cash, "expense", 5, "2026-11-01")
                .replace("}", ", \"tags\": [%s]}".formatted(manyTags(21))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void veryLargeAmountIsStoredExactly() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postTx(s, ws, tx(cashId(s, ws), "income", 9000000000000000000L, "2026-11-01"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount_paise").value(9000000000000000000L));
  }

  @Test
  void listParametersAreValidated() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    listTx(s, ws, "limit", "0").andExpect(status().isBadRequest());
    listTx(s, ws, "limit", "201").andExpect(status().isBadRequest());
    listTx(s, ws, "from", "yesterday").andExpect(status().isBadRequest());
    listTx(s, ws, "type", "bogus")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_type"));
    listTx(s, ws, "limit", "200").andExpect(status().isOk());
  }

  @Test
  void transferTypesAreRejectedHere() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    postTx(s, ws, tx(cash, "transfer_out", 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_type"));
    String id = createTx(s, ws, tx(cash, "expense", 100, "2026-11-01"));
    putTx(s, ws, id, tx(cash, "transfer_in", 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void transferLegsCannotBeEditedDeletedOrRestoredHere() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");
    String groupId = createTransfer(s, ws, cash, bank, 5000);
    String list =
        listTx(s, ws).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    List<String> outLegs = JsonPath.read(list, "$.items[?(@.type == 'transfer_out')].id");
    String outLeg = outLegs.getFirst();

    // Each of these would split the pair, so all three point the client at the transfer instead.
    putTx(s, ws, outLeg, tx(cash, "expense", 5000, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("transfer_leg"))
        .andExpect(jsonPath("$.error.details.transfer_group_id").value(groupId));
    mockMvc
        .perform(delete(url(ws) + "/" + outLeg).cookie(auth(s)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("transfer_leg"));
    mockMvc
        .perform(post(url(ws) + "/" + outLeg + "/restore").cookie(auth(s)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("transfer_leg"));
    // Nothing changed: both legs are still live.
    assertThat(count(s, ws)).isEqualTo(2);

    // Also after the whole transfer is deleted: its legs can only come back through /transfers.
    mockMvc
        .perform(delete("/api/v1/workspaces/" + ws + "/transfers/" + groupId).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(url(ws) + "/" + outLeg + "/restore").cookie(auth(s)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("transfer_leg"));
    assertThat(count(s, ws)).isZero();
  }

  @Test
  void accountsAndCategoriesMustBelongToTheWorkspaceAndKind() throws Exception {
    Session a = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    Session b = fixtures.signup();
    UUID wsB = fixtures.workspaceIdOf(b);
    String cashA = cashId(a, wsA);

    // B's account and category ids, used in A's workspace.
    postTx(a, wsA, tx(cashId(b, wsB), "expense", 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_account"))
        .andExpect(jsonPath("$.error.details.fields.account_id").exists());
    postTx(a, wsA, tx(UUID.randomUUID().toString(), "expense", 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent());
    postTx(
            a,
            wsA,
            tx(cashA, "expense", 100, "2026-11-01")
                .replace(
                    "}",
                    ", \"category_id\": \"%s\"}".formatted(categoryId(b, wsB, "Food", "expense"))))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_category"));
    // Right workspace, wrong kind: an income category on an expense.
    String salary = categoryId(a, wsA, "Salary", "income");
    postTx(
            a,
            wsA,
            tx(cashA, "expense", 100, "2026-11-01")
                .replace("}", ", \"category_id\": \"%s\"}".formatted(salary)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_category"));
    postTx(
            a,
            wsA,
            tx(cashA, "income", 100, "2026-11-01")
                .replace("}", ", \"category_id\": \"%s\"}".formatted(salary)))
        .andExpect(status().isCreated());
  }

  @Test
  void archivedAccountsRejectNewTransactionsAndMovesButNotOtherEdits() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String old = createAccount(s, ws, "Old card");
    String onOld = createTx(s, ws, tx(old, "expense", 100, "2026-11-01"));
    String onCash = createTx(s, ws, tx(cash, "expense", 100, "2026-11-01"));
    patchAccount(s, ws, old, "{\"archived\": true}").andExpect(status().isOk());

    postTx(s, ws, tx(old, "expense", 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("account_archived"));
    // Moving another transaction onto it is refused...
    putTx(s, ws, onCash, tx(old, "expense", 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("account_archived"));
    // ...but editing one that already lives there is fine, and so are delete and restore.
    putTx(
            s,
            ws,
            onOld,
            tx(old, "expense", 250, "2026-11-01").replace("}", ", \"note\": \"edited\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.note").value("edited"));
    mockMvc
        .perform(delete(url(ws) + "/" + onOld).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(url(ws) + "/" + onOld + "/restore").cookie(auth(s)))
        .andExpect(status().isOk());
  }

  // ---- filters and search ----

  @Test
  void filtersNarrowTheList() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "Bank");
    String food = categoryId(s, ws, "Food", "expense");
    String groceries = createChildCategory(s, ws, "Groceries", food);
    String salary = categoryId(s, ws, "Salary", "income");

    createTx(
        s,
        ws,
        tx(cash, "expense", 100, "2026-10-10")
            .replace(
                "}",
                ", \"category_id\": \"%s\", \"note\": \"Lunch at cafe\", \"tags\": [\"Trip\"]}"
                    .formatted(food)));
    createTx(
        s,
        ws,
        tx(bank, "income", 5000, "2026-10-15")
            .replace("}", ", \"category_id\": \"%s\"}".formatted(salary)));
    createTx(
        s,
        ws,
        tx(bank, "expense", 250, "2026-10-20").replace("}", ", \"note\": \"taxi 50% off\"}"));
    createTx(
        s,
        ws,
        tx(cash, "expense", 80, "2026-10-21")
            .replace("}", ", \"category_id\": \"%s\"}".formatted(groceries)));

    assertThat(count(s, ws)).isEqualTo(4);
    assertThat(count(s, ws, "from", "2026-10-15", "to", "2026-10-20")).isEqualTo(2);
    assertThat(count(s, ws, "from", "2026-10-21")).isEqualTo(1);
    assertThat(count(s, ws, "to", "2026-10-10")).isEqualTo(1);
    assertThat(count(s, ws, "type", "income")).isEqualTo(1);
    assertThat(count(s, ws, "type", "expense")).isEqualTo(3);
    assertThat(count(s, ws, "account_id", bank)).isEqualTo(2);
    assertThat(count(s, ws, "account_id", cash, "type", "expense")).isEqualTo(2);
    // The parent matches itself and its children; the child matches only itself.
    assertThat(count(s, ws, "category_id", food)).isEqualTo(2);
    assertThat(count(s, ws, "category_id", groceries)).isEqualTo(1);
    assertThat(count(s, ws, "tag", "TRIP")).isEqualTo(1);
    assertThat(count(s, ws, "tag", "nope")).isZero();
    assertThat(count(s, ws, "q", "LUNCH")).isEqualTo(1);
    // % and _ in the search text are literal characters, not wildcards.
    assertThat(count(s, ws, "q", "50%")).isEqualTo(1);
    assertThat(count(s, ws, "q", "%")).isEqualTo(1);
    assertThat(count(s, ws, "q", "_")).isZero();
    // Deleted rows never match.
    String id = createTx(s, ws, tx(cash, "expense", 1, "2026-10-22"));
    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s)));
    assertThat(count(s, ws, "from", "2026-10-22")).isZero();
  }

  // ---- tags ----

  @Test
  void tagsMatchIgnoringCaseAndAreListedForAutocomplete() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    createTx(s, ws, tx(cash, "expense", 1, "2026-11-01").replace("}", ", \"tags\": [\"Rent\"]}"));
    // Same tag in another case, plus a repeat inside one request: one tag, first spelling wins.
    postTx(
            s,
            ws,
            tx(cash, "expense", 2, "2026-11-01")
                .replace("}", ", \"tags\": [\"rent\", \" RENT \", \"Gym\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tags.length()").value(2))
        .andExpect(jsonPath("$.tags[0]").value("Gym"))
        .andExpect(jsonPath("$.tags[1]").value("Rent"));

    mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/tags").cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0]").value("Gym"))
        .andExpect(jsonPath("$.items[1]").value("Rent"))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void sameTagNameIsAllowedInDifferentWorkspaces() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    UUID wsB = fixtures.workspaceIdOf(b);

    createTx(
        a,
        wsA,
        tx(cashId(a, wsA), "expense", 1, "2026-11-01").replace("}", ", \"tags\": [\"x\"]}"));
    postTx(
            b,
            wsB,
            tx(cashId(b, wsB), "expense", 1, "2026-11-01").replace("}", ", \"tags\": [\"x\"]}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/v1/workspaces/" + wsB + "/tags").cookie(auth(b)))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  // ---- categories in use ----

  @Test
  void aCategoryUsedByATransactionCannotBeDeleted() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String custom =
        JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/workspaces/" + ws + "/categories")
                        .cookie(auth(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pets\", \"kind\": \"expense\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    String id =
        createTx(
            s,
            ws,
            tx(cashId(s, ws), "expense", 100, "2026-11-01")
                .replace("}", ", \"category_id\": \"%s\"}".formatted(custom)));

    String deleteUrl = "/api/v1/workspaces/" + ws + "/categories/" + custom;
    mockMvc
        .perform(delete(deleteUrl).cookie(auth(s)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("category_in_use"));
    // A soft-deleted transaction still references it, so the category stays protected.
    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s)));
    mockMvc
        .perform(delete(deleteUrl).cookie(auth(s)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("category_in_use"));
  }

  // ---- pagination ----

  @Test
  void pagesAreNewestFirstWithNoGapsOrRepeats() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    // Five on the same day (so only creation order separates them), one older, one newer.
    createTx(s, ws, tx(cash, "expense", 1, "2026-10-01").replace("}", ", \"note\": \"old\"}"));
    for (int i = 1; i <= 5; i++) {
      createTx(
          s, ws, tx(cash, "expense", i, "2026-11-01").replace("}", ", \"note\": \"n" + i + "\"}"));
    }
    createTx(s, ws, tx(cash, "expense", 1, "2026-12-01").replace("}", ", \"note\": \"new\"}"));

    List<String> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      String json =
          (cursor == null
                  ? listTx(s, ws, "limit", "3")
                  : listTx(s, ws, "limit", "3", "cursor", cursor))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> notes = JsonPath.read(json, "$.items[*].note");
      seen.addAll(notes);
      cursor = JsonPath.read(json, "$.next_cursor");
      pages++;
    } while (cursor != null);

    assertThat(seen).containsExactly("new", "n5", "n4", "n3", "n2", "n1", "old");
    assertThat(pages).isEqualTo(3);
  }

  @Test
  void aPageThatEndsExactlyAtTheLastRowHasNoNextCursor() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    createTx(s, ws, tx(cash, "expense", 1, "2026-11-01"));
    createTx(s, ws, tx(cash, "expense", 2, "2026-11-01"));

    listTx(s, ws, "limit", "2")
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void aGarbageCursorGets422() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    listTx(s, ws, "cursor", "not-a-cursor")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_cursor"));
    listTx(s, ws, "cursor", "bm90fGF8Y3Vyc29y").andExpect(status().isUnprocessableContent());
  }

  // ---- access ----

  @Test
  void nonMemberGets404OnEveryEndpoint() throws Exception {
    Session owner = fixtures.signup();
    UUID ownersWs = fixtures.workspaceIdOf(owner);
    String ownersCash = cashId(owner, ownersWs);
    String id = createTx(owner, ownersWs, tx(ownersCash, "expense", 100, "2026-11-01"));
    Session intruder = fixtures.signup();

    listTx(intruder, ownersWs).andExpect(status().isNotFound());
    mockMvc
        .perform(get(url(ownersWs) + "/" + id).cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    postTx(intruder, ownersWs, tx(ownersCash, "expense", 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    putTx(intruder, ownersWs, id, tx(ownersCash, "expense", 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(url(ownersWs) + "/" + id).cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(url(ownersWs) + "/" + id + "/restore").cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/workspaces/" + ownersWs + "/tags").cookie(auth(intruder)))
        .andExpect(status().isNotFound());

    // The owner's transaction is untouched.
    mockMvc
        .perform(get(url(ownersWs) + "/" + id).cookie(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount_paise").value(100));
  }

  @Test
  void transactionIdFromAnotherWorkspaceGets404() throws Exception {
    Session a = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    Session b = fixtures.signup();
    UUID wsB = fixtures.workspaceIdOf(b);
    String bsTx = createTx(b, wsB, tx(cashId(b, wsB), "expense", 100, "2026-11-01"));

    // A uses their own workspace URL but B's transaction id.
    mockMvc.perform(get(url(wsA) + "/" + bsTx).cookie(auth(a))).andExpect(status().isNotFound());
    putTx(a, wsA, bsTx, tx(cashId(a, wsA), "expense", 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete(url(wsA) + "/" + bsTx).cookie(auth(a))).andExpect(status().isNotFound());
    mockMvc
        .perform(post(url(wsA) + "/" + bsTx + "/restore").cookie(auth(a)))
        .andExpect(status().isNotFound());
  }

  @Test
  void requestsWithoutATokenGet401() throws Exception {
    mockMvc.perform(get(url(UUID.randomUUID()))).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  // ---- helpers ----

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/transactions";
  }

  private static String tx(String accountId, String type, long amountPaise, String date) {
    return "{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": %d, \"occurred_on\": \"%s\"}"
        .formatted(accountId, type, amountPaise, date);
  }

  private static String manyTags(int n) {
    List<String> tags = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      tags.add("\"t" + i + "\"");
    }
    return String.join(",", tags);
  }

  private ResultActions postTx(Session s, UUID ws, String json) throws Exception {
    return mockMvc.perform(
        post(url(ws)).cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions putTx(Session s, UUID ws, String id, String json) throws Exception {
    return mockMvc.perform(
        put(url(ws) + "/" + id)
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** Creates a transfer through /transfers and returns its id (the transfer_group_id). */
  private String createTransfer(Session s, UUID ws, String from, String to, long amountPaise)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + "/transfers")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"from_account_id\": \"%s\", \"to_account_id\": \"%s\", \"amount_paise\": %d, \"occurred_on\": \"2026-11-01\"}"
                            .formatted(from, to, amountPaise)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String createTx(Session s, UUID ws, String json) throws Exception {
    String body =
        postTx(s, ws, json)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  /** GET the list with query parameters given as name, value, name, value... */
  private ResultActions listTx(Session s, UUID ws, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(url(ws)).cookie(auth(s));
    for (int i = 0; i < params.length; i += 2) {
      request = request.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(request);
  }

  private int count(Session s, UUID ws, String... params) throws Exception {
    String json =
        listTx(s, ws, params)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<List<?>>read(json, "$.items").size();
  }

  private String cashId(Session s, UUID ws) throws Exception {
    String json =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(json, "$.items[?(@.name == 'Cash')].id");
    return ids.getFirst();
  }

  private String createAccount(Session s, UUID ws, String name) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + "/accounts")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"%s\", \"type\": \"bank\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private ResultActions patchAccount(Session s, UUID ws, String id, String json) throws Exception {
    return mockMvc.perform(
        patch("/api/v1/workspaces/" + ws + "/accounts/" + id)
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** Defaults have no subcategories, so tests that need one create it. */
  private String createChildCategory(Session s, UUID ws, String name, String parentId)
      throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + "/categories")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"%s\", \"kind\": \"expense\", \"parent_id\": \"%s\"}"
                            .formatted(name, parentId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private String categoryId(Session s, UUID ws, String name, String kind) throws Exception {
    String json =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/categories").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids =
        JsonPath.read(
            json, "$.items[?(@.name == '%s' && @.kind == '%s')].id".formatted(name, kind));
    return ids.getFirst();
  }
}
