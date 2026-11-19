package in.hisaab.recurring;

import static in.hisaab.TestFixtures.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.jobs.JobPoller;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

class RecurringApiTest extends PostgresTestBase {

  // New users default to Asia/Kolkata.
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;
  @Autowired private JobPoller poller;
  @Autowired private RecurringService service;
  @Autowired private CurrentUser currentUser;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void pastAnchorIsCaughtUpAndRunningTheJobAgainAddsNothing() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    String body =
        create(s, ws, rule(cash, "expense", TODAY.minusDays(14), "weekly", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.upcoming[0]").value(TODAY.minusDays(14).toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ruleId = JsonPath.read(body, "$.id");

    poller.runDue();
    transactions(s, ws).andExpect(jsonPath("$.items.length()").value(3));
    // The next occurrence is a week away; the job chain has moved on.
    list(s, ws).andExpect(jsonPath("$.items[0].upcoming[0]").value(TODAY.plusDays(7).toString()));

    // Rewind the rule and run the job again: the unique (rule, date) pair rejects the repeats.
    currentUser.runAs(
        userId(s),
        () ->
            tx.executeWithoutResult(st ->
                jdbc.update(
                    "UPDATE recurring_rules SET next_run_on = anchor_date WHERE id = ?::uuid",
                    ruleId)));
    currentUser.runAs(userId(s), () -> service.handle(ruleId));
    transactions(s, ws).andExpect(jsonPath("$.items.length()").value(3));
  }

  @Test
  void pausedRulesCreateNothingAndResumeDoesNotBackfill() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String body =
        create(s, ws, rule(cashId(s, ws), "income", TODAY.minusDays(14), "weekly", null))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ruleId = JsonPath.read(body, "$.id");

    action(s, ws, ruleId, "pause").andExpect(jsonPath("$.paused").value(true));
    poller.runDue();
    transactions(s, ws).andExpect(jsonPath("$.items.length()").value(0));

    action(s, ws, ruleId, "resume").andExpect(jsonPath("$.paused").value(false));
    poller.runDue();
    // Only today's occurrence; the two missed weeks are skipped.
    transactions(s, ws).andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void endDateStopsTheRuleAndDeleteKeepsMadeTransactions() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String body =
        create(
                s,
                ws,
                rule(cashId(s, ws), "expense", TODAY.minusDays(14), "weekly", TODAY.minusDays(7)))
            .andExpect(jsonPath("$.upcoming.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ruleId = JsonPath.read(body, "$.id");

    poller.runDue();
    transactions(s, ws).andExpect(jsonPath("$.items.length()").value(2));
    list(s, ws).andExpect(jsonPath("$.items[0].upcoming.length()").value(0));

    mockMvc
        .perform(delete("/api/v1/workspaces/" + ws + "/recurring/" + ruleId).cookie(auth(s)))
        .andExpect(status().isNoContent());
    list(s, ws).andExpect(jsonPath("$.items.length()").value(0));
    transactions(s, ws).andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void invalidRulesAreRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);

    create(s, ws, rule(cash, "transfer_out", TODAY, "monthly", null))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_type"));
    create(s, ws, rule(cash, "expense", TODAY, "monthly", TODAY.minusDays(1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_end"));
    create(
            s,
            ws,
            rule(cash, "expense", TODAY, "monthly", null)
                .replace("\"amount_paise\": 500", "\"amount_paise\": 0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void otherWorkspacesAreNotFound() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    String body =
        create(a, wsA, rule(cashId(a, wsA), "expense", TODAY.plusDays(30), "monthly", null))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ruleId = JsonPath.read(body, "$.id");

    list(b, wsA).andExpect(status().isNotFound());
    create(b, wsA, rule(cashId(a, wsA), "expense", TODAY, "monthly", null))
        .andExpect(status().isNotFound());
    action(b, wsA, ruleId, "pause").andExpect(status().isNotFound());
  }

  // users has no RLS, so a plain query can look the id up by email.
  private UUID userId(Session s) {
    return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, s.email());
  }

  private ResultActions list(Session s, UUID ws) throws Exception {
    return mockMvc.perform(get("/api/v1/workspaces/" + ws + "/recurring").cookie(auth(s)));
  }

  private ResultActions transactions(Session s, UUID ws) throws Exception {
    return mockMvc.perform(get("/api/v1/workspaces/" + ws + "/transactions").cookie(auth(s)));
  }

  private ResultActions create(Session s, UUID ws, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/workspaces/" + ws + "/recurring")
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions action(Session s, UUID ws, String ruleId, String verb) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.post(
                "/api/v1/workspaces/" + ws + "/recurring/" + ruleId + "/" + verb)
            .cookie(auth(s)));
  }

  private static String rule(
      String accountId, String type, LocalDate anchor, String frequency, LocalDate endsOn) {
    return ("{\"account_id\": \"%s\", \"type\": \"%s\", \"amount_paise\": 500,"
            + " \"frequency\": \"%s\", \"interval_count\": 1, \"anchor_date\": \"%s\"%s}")
        .formatted(
            accountId,
            type,
            frequency,
            anchor,
            endsOn == null ? "" : ", \"ends_on\": \"" + endsOn + "\"");
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
}
