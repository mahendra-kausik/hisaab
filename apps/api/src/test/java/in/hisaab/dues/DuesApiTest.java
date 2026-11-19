package in.hisaab.dues;

import static in.hisaab.TestFixtures.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class DuesApiTest extends PostgresTestBase {

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  // Spec check 6: ₹1,00,000 receivable, ₹40,000 + ₹25,000 paid -> ₹35,000 outstanding, partially
  // paid; overdue after its date; another ₹40,000 is refused.
  @Test
  void partPaymentsOverdueAndOverpayment() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Buyer");
    String due = due(s, ws, party, "receivable", 10_000_000, TODAY.plusDays(30));

    payment(s, ws, due, "income", 4_000_000).andExpect(status().isCreated());
    payment(s, ws, due, "income", 2_500_000).andExpect(status().isCreated());
    mockMvc
        .perform(get(url(ws) + "/" + due).cookie(auth(s)))
        .andExpect(jsonPath("$.paid_paise").value(6_500_000))
        .andExpect(jsonPath("$.outstanding_paise").value(3_500_000))
        .andExpect(jsonPath("$.state").value("partially_paid"));

    // Move the due date into the past.
    send(s, put(url(ws) + "/" + due), update(10_000_000, TODAY.minusDays(1)))
        .andExpect(jsonPath("$.state").value("overdue"));

    payment(s, ws, due, "income", 4_000_000)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("overpayment"))
        .andExpect(jsonPath("$.error.details.outstanding_paise").value(3_500_000));
    payment(s, ws, due, "income", 3_500_000).andExpect(status().isCreated());
    mockMvc
        .perform(get(url(ws) + "/" + due).cookie(auth(s)))
        .andExpect(jsonPath("$.state").value("settled"));
  }

  // Spec check 8: lending with "money handed over" makes one expense and one receivable; the
  // origin is not a payment; a repayment counts as income.
  @Test
  void originTransactionIsNotAPayment() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Friend");

    String body =
        """
        {"party_id": "%s", "direction": "receivable", "amount_paise": 1000000, "reason": "Loan",
         "origin": {"account_id": "%s", "occurred_on": "2026-11-01"}}
        """
            .formatted(party, cashId(s, ws));
    String due =
        id(
            send(s, post(url(ws)), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin_transaction_id").exists())
                .andExpect(jsonPath("$.paid_paise").value(0))
                .andExpect(jsonPath("$.outstanding_paise").value(1_000_000))
                .andExpect(jsonPath("$.state").value("open")));

    mockMvc
        .perform(get(txUrl(ws)).param("party_id", party).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].type").value("expense"))
        .andExpect(jsonPath("$.items[0].amount_paise").value(1_000_000))
        .andExpect(jsonPath("$.items[0].due_id").doesNotExist());

    payment(s, ws, due, "income", 400_000)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.party_id").value(party));
    mockMvc
        .perform(get(url(ws) + "/" + due).cookie(auth(s)))
        .andExpect(jsonPath("$.outstanding_paise").value(600_000));
    mockMvc
        .perform(get(txUrl(ws)).param("due_id", due).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void borrowingOriginIsIncomeAndRepaidAsExpense() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Lender");
    String body =
        """
        {"party_id": "%s", "direction": "payable", "amount_paise": 500000, "reason": "Loan",
         "origin": {"account_id": "%s", "occurred_on": "2026-11-01"}}
        """
            .formatted(party, cashId(s, ws));
    String due = id(send(s, post(url(ws)), body).andExpect(status().isCreated()));

    mockMvc
        .perform(get(txUrl(ws)).param("party_id", party).cookie(auth(s)))
        .andExpect(jsonPath("$.items[0].type").value("income"));
    payment(s, ws, due, "expense", 500_000).andExpect(status().isCreated());
  }

  @Test
  void paymentRulesCancelReopenAndEdits() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Tenant");
    String due = due(s, ws, party, "receivable", 100_000, null);

    // Money coming in for a receivable only.
    payment(s, ws, due, "expense", 1_000)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("payment_type_mismatch"));

    String paid = id(payment(s, ws, due, "income", 60_000).andExpect(status().isCreated()));

    // The amount cannot go below what is paid.
    send(s, put(url(ws) + "/" + due), update(50_000, null))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("amount_below_paid"));

    // Cancelled: no new payments; the old one stays. Reopen allows them again.
    send(s, post(url(ws) + "/" + due + "/cancel"), "")
        .andExpect(jsonPath("$.state").value("cancelled"))
        .andExpect(jsonPath("$.paid_paise").value(60_000));
    payment(s, ws, due, "income", 1_000)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("due_cancelled"));
    send(s, post(url(ws) + "/" + due + "/reopen"), "")
        .andExpect(jsonPath("$.state").value("partially_paid"));

    // Deleting a payment gives the money back to outstanding; restoring re-checks the limit.
    mockMvc
        .perform(delete(txUrl(ws) + "/" + paid).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(url(ws) + "/" + due).cookie(auth(s)))
        .andExpect(jsonPath("$.outstanding_paise").value(100_000));
    payment(s, ws, due, "income", 100_000).andExpect(status().isCreated());
    send(s, post(txUrl(ws) + "/" + paid + "/restore"), "")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("overpayment"));
  }

  @Test
  void editingAPaymentDoesNotCountItTwice() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String due = due(s, ws, party(s, ws, "P"), "receivable", 100_000, null);
    String paid = id(payment(s, ws, due, "income", 100_000).andExpect(status().isCreated()));

    // Fully paid already, yet editing that payment (same amount) is fine; raising it is not.
    send(s, put(txUrl(ws) + "/" + paid), paymentJson(s, ws, due, "income", 100_000))
        .andExpect(status().isOk());
    send(s, put(txUrl(ws) + "/" + paid), paymentJson(s, ws, due, "income", 100_001))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void otherWorkspacesDuesAreOutOfReach() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    UUID wsB = fixtures.workspaceIdOf(b);
    String bsDue = due(b, wsB, party(b, wsB, "B's buyer"), "receivable", 1_000, null);

    mockMvc.perform(get(url(wsA) + "/" + bsDue).cookie(auth(a))).andExpect(status().isNotFound());
    mockMvc.perform(get(url(wsB)).cookie(auth(a))).andExpect(status().isNotFound());
    send(a, post(url(wsA) + "/" + bsDue + "/cancel"), "").andExpect(status().isNotFound());
    payment(a, wsA, bsDue, "income", 100)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_due"));
    // A due cannot point at another workspace's party either.
    send(
            a,
            post(url(wsA)),
            "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": 100,"
                    .formatted(party(b, wsB, "Other"))
                + " \"reason\": \"x\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_party"));
  }

  @Test
  void invalidInputIsRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "P");

    send(s, post(url(ws)), "{}").andExpect(status().isBadRequest());
    send(
            s,
            post(url(ws)),
            "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": 0,"
                    .formatted(party)
                + " \"reason\": \"x\"}")
        .andExpect(status().isBadRequest());
    send(
            s,
            post(url(ws)),
            "{\"party_id\": \"%s\", \"direction\": \"sideways\", \"amount_paise\": 5,"
                    .formatted(party)
                + " \"reason\": \"x\"}")
        .andExpect(status().isBadRequest());
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/dues";
  }

  private static String txUrl(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/transactions";
  }

  private ResultActions send(Session s, MockHttpServletRequestBuilder request, String json)
      throws Exception {
    return mockMvc.perform(
        request.cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private String party(Session s, UUID ws, String name) throws Exception {
    return id(
        send(
                s,
                post("/api/v1/workspaces/" + ws + "/parties"),
                "{\"name\": \"%s\", \"role\": \"buyer\"}".formatted(name))
            .andExpect(status().isCreated()));
  }

  private String due(
      Session s, UUID ws, String party, String direction, long amountPaise, LocalDate dueDate)
      throws Exception {
    String date = dueDate == null ? "" : ", \"due_date\": \"" + dueDate + "\"";
    return id(
        send(
                s,
                post(url(ws)),
                "{\"party_id\": \"%s\", \"direction\": \"%s\", \"amount_paise\": %d,"
                        .formatted(party, direction, amountPaise)
                    + " \"reason\": \"Plot payment\""
                    + date
                    + "}")
            .andExpect(status().isCreated()));
  }

  private static String update(long amountPaise, LocalDate dueDate) {
    String date = dueDate == null ? "" : ", \"due_date\": \"" + dueDate + "\"";
    return "{\"amount_paise\": %d, \"reason\": \"Plot payment\"%s}".formatted(amountPaise, date);
  }

  private ResultActions payment(Session s, UUID ws, String due, String type, long amountPaise)
      throws Exception {
    return send(s, post(txUrl(ws)), paymentJson(s, ws, due, type, amountPaise));
  }

  private String paymentJson(Session s, UUID ws, String due, String type, long amountPaise)
      throws Exception {
    return "{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": %d,"
            .formatted(cashId(s, ws), type, amountPaise)
        + " \"occurred_on\": \"2026-11-01\", \"due_id\": \"%s\"}".formatted(due);
  }

  private String cashId(Session s, UUID ws) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.items[0].id");
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }
}
