package in.hisaab.dues;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import in.hisaab.common.security.CurrentUser;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// hour=0: the "after send time" check always passes, whatever time of day the tests run.
@TestPropertySource(properties = {"app.mail.from=digest@hisaab.test", "app.dues-digest.hour=0"})
class DuesDigestTest extends PostgresTestBase {

  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

  @MockitoBean private JavaMailSender mail;

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;
  @Autowired private DuesDigestJob job;
  @Autowired private CurrentUser currentUser;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetMail() {
    reset(mail);
  }

  // Spec check 9: lists exactly overdue, due today and next 3 days; at most once a day.
  @Test
  void listsTheRightDuesAndSendsOncePerDay() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Buyer");
    due(s, ws, party, "Overdue one", 100_000, TODAY.minusDays(2));
    due(s, ws, party, "Today one", 250_000, TODAY);
    due(s, ws, party, "Soon one", 30_000, TODAY.plusDays(3));
    due(s, ws, party, "Far one", 40_000, TODAY.plusDays(4));
    due(s, ws, party, "No date one", 50_000, null);
    String cancelled = due(s, ws, party, "Cancelled one", 60_000, TODAY);
    mockMvc
        .perform(post(url(ws) + "/" + cancelled + "/cancel").cookie(auth(s)))
        .andExpect(status().isOk());

    run(s);
    run(s); // the job running twice must not send twice

    var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail, times(1)).send(captor.capture());
    var message = captor.getValue();
    assertThat(message.getTo()).containsExactly(s.email());
    assertThat(message.getFrom()).isEqualTo("digest@hisaab.test");
    assertThat(message.getSubject()).isEqualTo("Hisaab: 1 overdue, 1 due today");
    assertThat(message.getText())
        .contains("Overdue (1)", "Overdue one", "1,000.00")
        .contains("Due today (1)", "Today one", "2,500.00")
        .contains("Next 3 days (1)", "Soon one")
        .doesNotContain("Far one", "No date one", "Cancelled one");
  }

  @Test
  void settledDuesAreLeftOutAndNothingDueMeansNoEmail() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = party(s, ws, "Buyer");
    String due = due(s, ws, party, "Paid up", 100_000, TODAY.minusDays(1));
    mockMvc
        .perform(
            post("/api/v1/workspaces/" + ws + "/transactions")
                .cookie(auth(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"account_id\": \"%s\", \"type\": \"income\", \"amount_paise\": 100000,"
                            .formatted(cashId(s, ws))
                        + " \"occurred_on\": \"2026-11-01\", \"due_id\": \"%s\"}".formatted(due)))
        .andExpect(status().isCreated());

    run(s);

    verify(mail, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void switchedOffUsersGetNothing() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    due(s, ws, party(s, ws, "Buyer"), "Overdue", 100_000, TODAY.minusDays(1));
    jdbc.update("UPDATE users SET dues_digest_enabled = false WHERE email = ?", s.email());

    run(s);

    verify(mail, never()).send(any(SimpleMailMessage.class));
  }

  // A failed send rolls the claim back, so the poller's retry sends it.
  @Test
  void aFailedSendIsRetried() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    due(s, ws, party(s, ws, "Buyer"), "Overdue", 100_000, TODAY.minusDays(1));
    doThrow(new org.springframework.mail.MailSendException("smtp down"))
        .doNothing()
        .when(mail)
        .send(any(SimpleMailMessage.class));

    assertThatThrownBy(() -> run(s)).isInstanceOf(RuntimeException.class);
    run(s);

    verify(mail, times(2)).send(any(SimpleMailMessage.class));
    // The successful run claimed the day, so a third run sends nothing more.
    run(s);
    verify(mail, times(2)).send(any(SimpleMailMessage.class));
  }

  // Signup starts the daily chain; running it queues exactly one next job for tomorrow.
  @Test
  void signupQueuesTheJobAndEachRunQueuesTheNext() throws Exception {
    Session s = fixtures.signup();
    UUID user = userId(s);
    String queued =
        "SELECT count(*) FROM jobs WHERE kind = 'dues.digest' AND user_id = ? AND status = 'queued'";
    assertThat(jdbc.queryForObject(queued, Integer.class, user)).isEqualTo(1);

    run(s);

    assertThat(jdbc.queryForObject(queued, Integer.class, user)).isEqualTo(1);
  }

  private void run(Session s) throws Exception {
    UUID user = userId(s);
    currentUser.runAs(user, () -> job.handle(user.toString()));
  }

  private UUID userId(Session s) {
    return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, s.email());
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/dues";
  }

  private ResultActions send(Session s, String path, String json) throws Exception {
    return mockMvc.perform(
        post(path).cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private String party(Session s, UUID ws, String name) throws Exception {
    return id(
        send(
                s,
                "/api/v1/workspaces/" + ws + "/parties",
                "{\"name\": \"%s\", \"role\": \"buyer\"}".formatted(name))
            .andExpect(status().isCreated()));
  }

  private String due(
      Session s, UUID ws, String party, String reason, long amountPaise, LocalDate dueDate)
      throws Exception {
    String date = dueDate == null ? "" : ", \"due_date\": \"" + dueDate + "\"";
    return id(
        send(
                s,
                url(ws),
                "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": %d,"
                        .formatted(party, amountPaise)
                    + " \"reason\": \"%s\"%s}".formatted(reason, date))
            .andExpect(status().isCreated()));
  }

  private String cashId(Session s, UUID ws) throws Exception {
    String body =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/workspaces/" + ws + "/accounts")
                    .cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.items[0].id");
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }
}
