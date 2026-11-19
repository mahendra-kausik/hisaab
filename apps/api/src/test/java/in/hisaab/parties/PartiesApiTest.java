package in.hisaab.parties;

import static in.hisaab.TestFixtures.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import in.hisaab.jobs.JobPoller;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PartiesApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;
  @Autowired private JobPoller poller;

  @Test
  void createListPatchAndArchive() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    String id =
        id(
            send(
                    s,
                    post(url(ws)),
                    """
                    {"name": " Ramesh ", "phone": "98450 12345", "role": "contractor",
                     "notes": "Plumbing"}
                    """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ramesh"))
                .andExpect(jsonPath("$.role").value("contractor"))
                .andExpect(jsonPath("$.phone").value("98450 12345"))
                .andExpect(jsonPath("$.archived_at").doesNotExist()));

    // Only the fields sent change; an empty string clears phone.
    send(s, patch(url(ws) + "/" + id), "{\"role\": \"friend_family\", \"phone\": \"\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ramesh"))
        .andExpect(jsonPath("$.role").value("friend_family"))
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.notes").value("Plumbing"));
    send(s, patch(url(ws) + "/" + id), "{\"archived\": true}")
        .andExpect(jsonPath("$.archived_at").exists());

    // Archived parties are still listed.
    mockMvc
        .perform(get(url(ws)).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].archived_at").exists());
  }

  @Test
  void invalidInputAndDuplicateNamesAreRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    party(s, ws, "Suresh");

    send(s, post(url(ws)), "{\"name\": \"suresh\", \"role\": \"buyer\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("name_taken"));
    send(s, post(url(ws)), "{\"name\": \"X\", \"role\": \"landlord\"}")
        .andExpect(status().isBadRequest());
    send(s, post(url(ws)), "{\"name\": \"  \", \"role\": \"buyer\"}")
        .andExpect(status().isBadRequest());
    send(s, post(url(ws)), "{\"name\": \"X\"}").andExpect(status().isBadRequest());
  }

  @Test
  void otherWorkspacesPartiesAreOutOfReach() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    String bsParty = party(b, fixtures.workspaceIdOf(b), "B's buyer");

    send(a, patch(url(wsA) + "/" + bsParty), "{\"name\": \"Mine\"}")
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(url(fixtures.workspaceIdOf(b))).cookie(auth(a)))
        .andExpect(status().isNotFound());
    transaction(a, wsA, post(txUrl(wsA)), bsParty)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_party"));
  }

  @Test
  void transactionsLinkFilterAndExportByParty() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String ramesh = party(s, ws, "Ramesh");
    String linked =
        id(
            transaction(s, ws, post(txUrl(ws)), ramesh)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.party_id").value(ramesh)));
    transaction(s, ws, post(txUrl(ws)), null).andExpect(status().isCreated());

    mockMvc
        .perform(get(txUrl(ws)).param("party_id", ramesh).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(linked));
    mockMvc
        .perform(get(txUrl(ws) + "/export.csv").param("party_id", ramesh).cookie(auth(s)))
        .andExpect(content().string(Matchers.containsString(",Ramesh,")));

    // Archived: can stay on the transaction it is already on, but cannot be newly linked.
    send(s, patch(url(ws) + "/" + ramesh), "{\"archived\": true}").andExpect(status().isOk());
    transaction(s, ws, put(txUrl(ws) + "/" + linked), ramesh).andExpect(status().isOk());
    transaction(s, ws, post(txUrl(ws)), ramesh)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("party_archived"));
    // PUT without party_id clears the link.
    transaction(s, ws, put(txUrl(ws) + "/" + linked), null)
        .andExpect(jsonPath("$.party_id").doesNotExist());
  }

  @Test
  void recurringRuleCarriesItsPartyIntoTheTransactionsItMakes() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String tenant = party(s, ws, "Tenant");
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    send(
            s,
            post("/api/v1/workspaces/" + ws + "/recurring"),
            """
            {"account_id": "%s", "type": "income", "amount_paise": 1500000, "party_id": "%s",
             "frequency": "monthly", "interval_count": 1, "anchor_date": "%s"}
            """
                .formatted(cashId(s, ws), tenant, today))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.party_id").value(tenant));
    poller.runDue();

    mockMvc
        .perform(get(txUrl(ws)).param("party_id", tenant).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/parties";
  }

  private static String txUrl(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/transactions";
  }

  private ResultActions send(Session s, MockHttpServletRequestBuilder request, String json)
      throws Exception {
    return mockMvc.perform(
        request.cookie(auth(s)).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions transaction(
      Session s, UUID ws, MockHttpServletRequestBuilder request, String partyId) throws Exception {
    String party = partyId == null ? "" : ", \"party_id\": \"" + partyId + "\"";
    return send(
        s,
        request,
        "{\"account_id\": \"%s\", \"type\": \"expense\", \"amount_paise\": 1000,"
                .formatted(cashId(s, ws))
            + " \"occurred_on\": \"2026-11-01\""
            + party
            + "}");
  }

  private String party(Session s, UUID ws, String name) throws Exception {
    return id(
        send(s, post(url(ws)), "{\"name\": \"%s\", \"role\": \"buyer\"}".formatted(name))
            .andExpect(status().isCreated()));
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
