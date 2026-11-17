package in.hisaab.workspaces;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class WorkspacesApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void businessWorkspaceGetsBusinessDefaultsAndACashAccount() throws Exception {
    Session s = fixtures.signup();

    String body =
        postWorkspace(s, "{\"name\": \"  Dad - Real Estate \", \"kind\": \"business\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Dad - Real Estate"))
            .andExpect(jsonPath("$.kind").value("business"))
            .andExpect(jsonPath("$.role").value("owner"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ws = JsonPath.read(body, "$.id");

    String categories =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/categories").cookie(auth(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(17))
            .andExpect(jsonPath("$.items[?(@.is_system == false)]").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // JsonPath.read directly: MockMvc's jsonPath(...).value(List) does not match filter results.
    List<String> expense = JsonPath.read(categories, "$.items[?(@.kind == 'expense')].name");
    List<String> income = JsonPath.read(categories, "$.items[?(@.kind == 'income')].name");
    assertThat(expense)
        .containsExactly(
            "Labour",
            "Materials",
            "Contractor Payments",
            "Broker Commission",
            "Registration & Legal",
            "Transport",
            "Office & Admin",
            "Money Lent",
            "Loan Repayment",
            "Other");
    assertThat(income)
        .containsExactly(
            "Sale Proceeds",
            "Advance Received",
            "Rent Received",
            "Commission",
            "Money Borrowed",
            "Repayment Received",
            "Other Income");

    mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Cash"));
  }

  @Test
  void theNewWorkspaceAppearsInMeNextToThePersonalOne() throws Exception {
    Session s = fixtures.signup();
    postWorkspace(s, "{\"name\": \"Shop\", \"kind\": \"business\"}")
        .andExpect(status().isCreated());

    String me =
        mockMvc
            .perform(get("/api/v1/me").cookie(auth(s)))
            .andExpect(jsonPath("$.workspaces.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> shop = JsonPath.read(me, "$.workspaces[?(@.name == 'Shop')].kind");
    List<String> personal = JsonPath.read(me, "$.workspaces[?(@.name == 'Personal')].kind");
    assertThat(shop).containsExactly("business");
    assertThat(personal).containsExactly("personal");
  }

  @Test
  void anotherUserCannotSeeTheNewWorkspaceData() throws Exception {
    Session owner = fixtures.signup();
    String body =
        postWorkspace(owner, "{\"name\": \"Shop\", \"kind\": \"business\"}")
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ws = JsonPath.read(body, "$.id");
    Session intruder = fixtures.signup();

    mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/workspaces/" + ws + "/categories").cookie(auth(intruder)))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidInputGets400() throws Exception {
    Session s = fixtures.signup();

    postWorkspace(s, "{\"name\": \"\", \"kind\": \"personal\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields.name").exists());
    postWorkspace(s, "{\"name\": \"X\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields.kind").exists());
    postWorkspace(s, "{\"name\": \"X\", \"kind\": \"enterprise\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void creatingAWorkspaceWithoutATokenGets401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"X\", \"kind\": \"personal\"}"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions postWorkspace(Session s, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/workspaces")
            .cookie(auth(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }
}
