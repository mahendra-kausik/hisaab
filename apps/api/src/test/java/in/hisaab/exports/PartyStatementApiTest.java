package in.hisaab.exports;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.io.IOException;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PartyStatementApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void statementHasTotalsDuesAndTransactions() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Ramesh Kumar");

    // ₹1,00,000 receivable with ₹40,000 paid back, plus one expense and one income not tied to it.
    String due =
        id(
            create(
                s,
                ws,
                "/dues",
                "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": 10000000,"
                        .formatted(party)
                    + " \"reason\": \"Plot advance\", \"due_date\": \"2026-12-31\"}"));
    tx(s, ws, party, due, "income", 4_000_000, "First instalment");
    tx(s, ws, party, null, "expense", 50_000, "Cement bags");
    tx(s, ws, party, null, "income", 120_000, "Refund");

    var result =
        mockMvc
            .perform(get(url(ws, party)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn()
            .getResponse();
    assertThat(result.getHeader("Content-Disposition"))
        .startsWith("attachment; filename=\"statement-ramesh-kumar-")
        .endsWith(".pdf\"");
    byte[] pdf = result.getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");

    String text = text(pdf);
    assertThat(text)
        .contains("Statement for Ramesh Kumar")
        .contains("Plot advance")
        .contains("First instalment")
        .contains("Cement bags")
        .contains("₹1,00,000.00") // the due's amount
        .contains("₹60,000.00") // outstanding, and the net balance
        .contains("₹500.00") // total paid (the one expense)
        .contains("₹41,200.00") // total received: 40,000 + 1,200
        .contains("They owe you");
  }

  @Test
  void partyAndWorkspaceBoundariesGive404() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    UUID wsB = fixtures.workspaceIdOf(b);
    String bsParty = party(b, wsB, "B's buyer");

    // Someone else's workspace, someone else's party in my workspace, and a party that is not
    // there.
    mockMvc.perform(get(url(wsB, bsParty)).cookie(auth(a))).andExpect(status().isNotFound());
    mockMvc.perform(get(url(wsA, bsParty)).cookie(auth(a))).andExpect(status().isNotFound());
    mockMvc
        .perform(get(url(wsA, UUID.randomUUID().toString())).cookie(auth(a)))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(url(wsB, bsParty))).andExpect(status().isUnauthorized());
  }

  @Test
  void textTheFontCannotDrawBecomesQuestionMarks() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Sita");
    tx(s, ws, party, null, "expense", 10_000, "नमस्ते cash");

    byte[] pdf =
        mockMvc
            .perform(get(url(ws, party)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(text(pdf)).contains("?").contains("cash").doesNotContain("नमस्ते");
  }

  // Only text may be shortened. A big amount must show in full, and a name wider than the page
  // must be cut with an ellipsis instead of running off it.
  @Test
  void moneyIsNeverCutButLongTextIs() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Ramesh Kumar and Sons Builders and Contractors Private Limited");
    create(
        s,
        ws,
        "/dues",
        "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": 1234567890,"
                .formatted(party)
            + " \"reason\": \"Plot advance for the Whitefield site, second instalment\"}");

    byte[] pdf =
        mockMvc
            .perform(get(url(ws, party)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(text(pdf))
        .contains("₹1,23,45,678.90") // amount, and outstanding below
        .contains("…")
        .doesNotContain("Private Limited"); // the end of the name was cut off the title
  }

  @Test
  void emptyPartyStillGivesAStatement() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Nobody");

    byte[] pdf =
        mockMvc
            .perform(get(url(ws, party)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(text(pdf))
        .contains("No dues with this party.")
        .contains("No transactions with this party.")
        .contains("Settled up");
  }

  private static String text(byte[] pdf) throws IOException {
    try (var doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  private static String url(UUID ws, String party) {
    return "/api/v1/workspaces/" + ws + "/parties/" + party + "/statement.pdf";
  }

  /** POSTs to a workspace path and expects 201. */
  private ResultActions create(Session s, UUID ws, String path, String json) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/workspaces/" + ws + path)
                .cookie(auth(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }

  private String party(Session s, UUID ws, String name) throws Exception {
    return id(create(s, ws, "/parties", "{\"name\": \"%s\", \"role\": \"buyer\"}".formatted(name)));
  }

  private void tx(
      Session s, UUID ws, String party, String due, String type, long paise, String note)
      throws Exception {
    String cash =
        JsonPath.read(
            mockMvc
                .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.items[0].id");
    String dueField = due == null ? "" : ", \"due_id\": \"" + due + "\"";
    create(
        s,
        ws,
        "/transactions",
        "{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": %d, \"party_id\": \"%s\","
                .formatted(cash, type, paise, party)
            + " \"occurred_on\": \"2026-11-01\", \"note\": \"%s\"%s}".formatted(note, dueField));
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }
}
