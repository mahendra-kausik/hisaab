package in.hisaab.projects;

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

class ProjectsApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;
  @Autowired private JobPoller poller;

  @Test
  void createListPatchAndArchive() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);

    String id =
        id(
            send(s, post(url(ws)), "{\"name\": \" Plot 14 \", \"notes\": \"Whitefield\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Plot 14"))
                .andExpect(jsonPath("$.notes").value("Whitefield"))
                .andExpect(jsonPath("$.archived_at").doesNotExist()));

    // Only the fields sent change; an empty string clears notes.
    send(s, patch(url(ws) + "/" + id), "{\"notes\": \"\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Plot 14"))
        .andExpect(jsonPath("$.notes").doesNotExist());
    send(s, patch(url(ws) + "/" + id), "{\"archived\": true}")
        .andExpect(jsonPath("$.archived_at").exists());

    // Archived projects are still listed.
    mockMvc
        .perform(get(url(ws)).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].archived_at").exists());
  }

  @Test
  void invalidInputAndDuplicateNamesAreRejected() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    project(s, ws, "Flat 3B");

    send(s, post(url(ws)), "{\"name\": \"flat 3b\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("name_taken"));
    send(s, post(url(ws)), "{\"name\": \"  \"}").andExpect(status().isBadRequest());
    send(s, post(url(ws)), "{}").andExpect(status().isBadRequest());
  }

  @Test
  void otherWorkspacesProjectsAreOutOfReach() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();
    UUID wsA = fixtures.workspaceIdOf(a);
    String bsProject = project(b, fixtures.workspaceIdOf(b), "B's site");

    send(a, patch(url(wsA) + "/" + bsProject), "{\"name\": \"Mine\"}")
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(url(fixtures.workspaceIdOf(b))).cookie(auth(a)))
        .andExpect(status().isNotFound());
    transaction(a, wsA, post(txUrl(wsA)), bsProject)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_project"));
  }

  @Test
  void transactionsLinkFilterAndExportByProject() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String site = project(s, ws, "Site A");
    String linked =
        id(
            transaction(s, ws, post(txUrl(ws)), site)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project_id").value(site)));
    transaction(s, ws, post(txUrl(ws)), null).andExpect(status().isCreated());

    mockMvc
        .perform(get(txUrl(ws)).param("project_id", site).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(linked));
    mockMvc
        .perform(get(txUrl(ws) + "/export.csv").param("project_id", site).cookie(auth(s)))
        .andExpect(content().string(Matchers.containsString(",Site A,")));

    // Archived: can stay on the transaction it is already on, but cannot be newly linked.
    send(s, patch(url(ws) + "/" + site), "{\"archived\": true}").andExpect(status().isOk());
    transaction(s, ws, put(txUrl(ws) + "/" + linked), site).andExpect(status().isOk());
    transaction(s, ws, post(txUrl(ws)), site)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("project_archived"));
    // PUT without project_id clears the link.
    transaction(s, ws, put(txUrl(ws) + "/" + linked), null)
        .andExpect(jsonPath("$.project_id").doesNotExist());
  }

  @Test
  void recurringRuleCarriesItsProjectIntoTheTransactionsItMakes() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String site = project(s, ws, "Rental flat");
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    send(
            s,
            post("/api/v1/workspaces/" + ws + "/recurring"),
            """
            {"account_id": "%s", "type": "income", "amount_paise": 1500000, "project_id": "%s",
             "frequency": "monthly", "interval_count": 1, "anchor_date": "%s"}
            """
                .formatted(cashId(s, ws), site, today))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.project_id").value(site));
    poller.runDue();

    mockMvc
        .perform(get(txUrl(ws)).param("project_id", site).cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/projects";
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
      Session s, UUID ws, MockHttpServletRequestBuilder request, String projectId)
      throws Exception {
    String project = projectId == null ? "" : ", \"project_id\": \"" + projectId + "\"";
    return send(
        s,
        request,
        "{\"account_id\": \"%s\", \"type\": \"expense\", \"amount_paise\": 1000,"
                .formatted(cashId(s, ws))
            + " \"occurred_on\": \"2026-11-01\""
            + project
            + "}");
  }

  private String project(Session s, UUID ws, String name) throws Exception {
    return id(
        send(s, post(url(ws)), "{\"name\": \"%s\"}".formatted(name))
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
