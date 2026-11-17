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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class TransfersApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  // ---- create, read, edit ----

  @Test
  void createReturns201MovesMoneyAndBothLegsAppearInTheTransactionList() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");

    String body =
        postTransfer(
                s,
                ws,
                transfer(cash, bank, 500000, "2026-11-01")
                    .replace(
                        "}", ", \"payment_method\": \"bank_transfer\", \"note\": \"  to bank  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.from_account_id").value(cash))
            .andExpect(jsonPath("$.to_account_id").value(bank))
            .andExpect(jsonPath("$.amount_paise").value(500000))
            .andExpect(jsonPath("$.occurred_on").value("2026-11-01"))
            .andExpect(jsonPath("$.payment_method").value("bank_transfer"))
            .andExpect(jsonPath("$.note").value("to bank"))
            .andExpect(jsonPath("$.out_transaction_id").exists())
            .andExpect(jsonPath("$.in_transaction_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");

    // The money moved, and the total across accounts did not change.
    assertThat(balance(s, ws, cash)).isEqualTo(-500000);
    assertThat(balance(s, ws, bank)).isEqualTo(500000);

    // Both legs are ordinary rows in the list, tied together by the transfer id.
    String list =
        mockMvc
            .perform(get(transactionsUrl(ws)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> types = JsonPath.read(list, "$.items[*].type");
    List<String> groups = JsonPath.read(list, "$.items[*].transfer_group_id");
    assertThat(types).containsExactlyInAnyOrder("transfer_out", "transfer_in");
    assertThat(groups).containsOnly(id);

    mockMvc
        .perform(get(url(ws) + "/" + id).cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.amount_paise").value(500000));
  }

  @Test
  void minimalCreateLeavesOptionalFieldsEmpty() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    postTransfer(s, ws, transfer(cashId(s, ws), createAccount(s, ws, "HDFC"), 100, "2026-11-01"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.note").doesNotExist())
        .andExpect(jsonPath("$.payment_method").doesNotExist());
  }

  @Test
  void putReplacesBothLegsAndClearsWhatIsLeftOut() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");
    String wallet = createAccount(s, ws, "Wallet");
    String id =
        createTransfer(
            s,
            ws,
            transfer(cash, bank, 500000, "2026-11-01")
                .replace("}", ", \"payment_method\": \"upi\", \"note\": \"first\"}"));

    putTransfer(s, ws, id, transfer(bank, wallet, 120000, "2026-11-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.from_account_id").value(bank))
        .andExpect(jsonPath("$.to_account_id").value(wallet))
        .andExpect(jsonPath("$.amount_paise").value(120000))
        .andExpect(jsonPath("$.occurred_on").value("2026-11-03"))
        .andExpect(jsonPath("$.note").doesNotExist())
        .andExpect(jsonPath("$.payment_method").doesNotExist());

    // Cash is no longer involved, and the money now moves bank -> wallet.
    assertThat(balance(s, ws, cash)).isZero();
    assertThat(balance(s, ws, bank)).isEqualTo(-120000);
    assertThat(balance(s, ws, wallet)).isEqualTo(120000);

    // Still exactly two rows, both carrying the new values.
    String list =
        mockMvc
            .perform(get(transactionsUrl(ws)).cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<?>>read(list, "$.items")).hasSize(2);
    assertThat(JsonPath.<List<Number>>read(list, "$.items[*].amount_paise"))
        .extracting(Number::longValue)
        .containsOnly(120000L);
    assertThat(JsonPath.<List<String>>read(list, "$.items[*].occurred_on"))
        .containsOnly("2026-11-03");
  }

  @Test
  void deleteHidesBothLegsAndRestoreBringsThemBack() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");
    String id = createTransfer(s, ws, transfer(cash, bank, 500000, "2026-11-01"));

    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s))).andExpect(status().isNoContent());
    mockMvc.perform(get(url(ws) + "/" + id).cookie(auth(s))).andExpect(status().isNotFound());
    assertThat(transactionCount(s, ws)).isZero();
    assertThat(balance(s, ws, cash)).isZero();
    assertThat(balance(s, ws, bank)).isZero();
    // A deleted transfer cannot be edited or deleted again.
    putTransfer(s, ws, id, transfer(cash, bank, 1, "2026-11-01")).andExpect(status().isNotFound());
    mockMvc.perform(delete(url(ws) + "/" + id).cookie(auth(s))).andExpect(status().isNotFound());

    mockMvc
        .perform(post(url(ws) + "/" + id + "/restore").cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount_paise").value(500000));
    assertThat(transactionCount(s, ws)).isEqualTo(2);
    assertThat(balance(s, ws, cash)).isEqualTo(-500000);
    assertThat(balance(s, ws, bank)).isEqualTo(500000);

    // Restoring a live transfer is a harmless no-op.
    mockMvc
        .perform(post(url(ws) + "/" + id + "/restore").cookie(auth(s)))
        .andExpect(status().isOk());
    assertThat(transactionCount(s, ws)).isEqualTo(2);
  }

  // ---- validation ----

  @Test
  void sameAccountOnBothSidesGets422() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    postTransfer(s, ws, transfer(cash, cash, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("same_account"));
    String id =
        createTransfer(s, ws, transfer(cash, createAccount(s, ws, "HDFC"), 100, "2026-11-01"));
    putTransfer(s, ws, id, transfer(cash, cash, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("same_account"));
  }

  @Test
  void invalidBodiesGet400() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");

    postTransfer(
            s,
            ws,
            "{\"from_account_id\": \"%s\", \"to_account_id\": \"%s\", \"occurred_on\": \"2026-11-01\"}"
                .formatted(cash, bank))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("validation_failed"))
        .andExpect(jsonPath("$.error.details.fields.amount_paise").exists());
    postTransfer(
            s,
            ws,
            "{\"from_account_id\": \"%s\", \"amount_paise\": 100, \"occurred_on\": \"2026-11-01\"}"
                .formatted(cash))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields.to_account_id").exists());
    postTransfer(s, ws, transfer(cash, bank, 0, "2026-11-01")).andExpect(status().isBadRequest());
    postTransfer(s, ws, transfer(cash, bank, -5, "2026-11-01")).andExpect(status().isBadRequest());
    postTransfer(s, ws, transfer(cash, bank, 5, "01-11-2026")).andExpect(status().isBadRequest());
    postTransfer(
            s,
            ws,
            transfer(cash, bank, 5, "2026-11-01").replace("}", ", \"payment_method\": \"x\"}"))
        .andExpect(status().isBadRequest());
    postTransfer(
            s,
            ws,
            transfer(cash, bank, 5, "2026-11-01")
                .replace("}", ", \"note\": \"%s\"}".formatted("x".repeat(501))))
        .andExpect(status().isBadRequest());
    // Nothing above created a row.
    assertThat(transactionCount(s, ws)).isZero();
  }

  @Test
  void veryLargeAmountIsStoredExactly() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");

    postTransfer(s, ws, transfer(cash, bank, 9000000000000000000L, "2026-11-01"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount_paise").value(9000000000000000000L));
    assertThat(balance(s, ws, bank)).isEqualTo(9000000000000000000L);
    assertThat(balance(s, ws, cash)).isEqualTo(-9000000000000000000L);
  }

  @Test
  void accountsMustBelongToTheWorkspace() throws Exception {
    Session a = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    Session b = fixtures.signup();
    UUID wsB = fixtures.workspaceIdOf(b);
    String cashA = cashId(a, wsA);
    String cashB = cashId(b, wsB);

    postTransfer(a, wsA, transfer(cashA, cashB, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_account"));
    postTransfer(a, wsA, transfer(cashB, cashA, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_account"));
    postTransfer(a, wsA, transfer(cashA, UUID.randomUUID().toString(), 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_account"));
    assertThat(transactionCount(a, wsA)).isZero();
    assertThat(transactionCount(b, wsB)).isZero();
  }

  @Test
  void archivedAccountsRejectNewTransfersAndMovesButNotOtherEdits() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String old = createAccount(s, ws, "Old card");
    String other = createAccount(s, ws, "Other");
    String onOld = createTransfer(s, ws, transfer(cash, old, 100, "2026-11-01"));
    String onOther = createTransfer(s, ws, transfer(cash, other, 100, "2026-11-01"));
    patchAccount(s, ws, old, "{\"archived\": true}").andExpect(status().isOk());

    // New transfers to or from it are refused...
    postTransfer(s, ws, transfer(cash, old, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("account_archived"));
    postTransfer(s, ws, transfer(old, cash, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("account_archived"));
    // ...and so is moving another transfer onto it.
    putTransfer(s, ws, onOther, transfer(cash, old, 100, "2026-11-01"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("account_archived"));
    // But a transfer that already lives there can be edited, deleted and restored.
    putTransfer(
            s,
            ws,
            onOld,
            transfer(cash, old, 250, "2026-11-01").replace("}", ", \"note\": \"edited\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.note").value("edited"));
    mockMvc
        .perform(delete(url(ws) + "/" + onOld).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post(url(ws) + "/" + onOld + "/restore").cookie(auth(s)))
        .andExpect(status().isOk());
  }

  // ---- access ----

  @Test
  void unknownIdGets404() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank = createAccount(s, ws, "HDFC");
    UUID unknown = UUID.randomUUID();

    mockMvc.perform(get(url(ws) + "/" + unknown).cookie(auth(s))).andExpect(status().isNotFound());
    putTransfer(s, ws, unknown.toString(), transfer(cash, bank, 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(url(ws) + "/" + unknown).cookie(auth(s)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(url(ws) + "/" + unknown + "/restore").cookie(auth(s)))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonMemberGets404OnEveryEndpoint() throws Exception {
    Session owner = fixtures.signup();
    UUID ownersWs = fixtures.workspaceIdOf(owner);
    String cash = cashId(owner, ownersWs);
    String bank = createAccount(owner, ownersWs, "HDFC");
    String id = createTransfer(owner, ownersWs, transfer(cash, bank, 100, "2026-11-01"));
    Session intruder = fixtures.signup();

    mockMvc
        .perform(get(url(ownersWs) + "/" + id).cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    postTransfer(intruder, ownersWs, transfer(cash, bank, 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    putTransfer(intruder, ownersWs, id, transfer(cash, bank, 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(url(ownersWs) + "/" + id).cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(url(ownersWs) + "/" + id + "/restore").cookie(auth(intruder)))
        .andExpect(status().isNotFound());

    // The owner's transfer is untouched.
    mockMvc
        .perform(get(url(ownersWs) + "/" + id).cookie(auth(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount_paise").value(100));
    assertThat(transactionCount(owner, ownersWs)).isEqualTo(2);
  }

  @Test
  void transferIdFromAnotherWorkspaceGets404() throws Exception {
    Session a = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    Session b = fixtures.signup();
    UUID wsB = fixtures.workspaceIdOf(b);
    String bsTransfer =
        createTransfer(
            b, wsB, transfer(cashId(b, wsB), createAccount(b, wsB, "HDFC"), 100, "2026-11-01"));
    String cashA = cashId(a, wsA);
    String bankA = createAccount(a, wsA, "HDFC");

    // A uses their own workspace URL but B's transfer id.
    mockMvc
        .perform(get(url(wsA) + "/" + bsTransfer).cookie(auth(a)))
        .andExpect(status().isNotFound());
    putTransfer(a, wsA, bsTransfer, transfer(cashA, bankA, 1, "2026-11-01"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(url(wsA) + "/" + bsTransfer).cookie(auth(a)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(url(wsA) + "/" + bsTransfer + "/restore").cookie(auth(a)))
        .andExpect(status().isNotFound());
    assertThat(transactionCount(b, wsB)).isEqualTo(2);
  }

  @Test
  void requestsWithoutATokenGet401() throws Exception {
    mockMvc
        .perform(get(url(UUID.randomUUID()) + "/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  // ---- helpers ----

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/transfers";
  }

  private static String transactionsUrl(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/transactions";
  }

  private static String transfer(String from, String to, long amountPaise, String date) {
    return "{\"from_account_id\": \"%s\", \"to_account_id\": \"%s\", \"amount_paise\": %d, \"occurred_on\": \"%s\"}"
        .formatted(from, to, amountPaise, date);
  }

  private ResultActions postTransfer(Session s, UUID ws, String json) throws Exception {
    return mockMvc.perform(
        post(url(ws)).cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions putTransfer(Session s, UUID ws, String id, String json) throws Exception {
    return mockMvc.perform(
        put(url(ws) + "/" + id)
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private String createTransfer(Session s, UUID ws, String json) throws Exception {
    String body =
        postTransfer(s, ws, json)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  /** How many live rows the transactions list shows (a transfer counts as two). */
  private int transactionCount(Session s, UUID ws) throws Exception {
    String json =
        mockMvc
            .perform(get(transactionsUrl(ws)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<List<?>>read(json, "$.items").size();
  }

  private long balance(Session s, UUID ws, String accountId) throws Exception {
    String json =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Number> balances =
        JsonPath.read(json, "$.items[?(@.id == '%s')].balance_paise".formatted(accountId));
    return balances.getFirst().longValue();
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
}
