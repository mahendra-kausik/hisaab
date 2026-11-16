package in.hisaab.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class UserSettingsTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void patchUpdatesAllSettingsAndTheyPersist() throws Exception {
    Session s = fixtures.signup();

    patchMe(
            s,
            """
            {"name": "Ravi K", "timezone": "Asia/Dubai", "period_start_day": 25,
             "dues_digest_enabled": false}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ravi K"))
        .andExpect(jsonPath("$.timezone").value("Asia/Dubai"))
        .andExpect(jsonPath("$.period_start_day").value(25))
        .andExpect(jsonPath("$.dues_digest_enabled").value(false));

    // A separate request reads the saved values back.
    mockMvc
        .perform(get("/api/v1/me").cookie(new Cookie("hisaab_access", s.accessToken())))
        .andExpect(jsonPath("$.name").value("Ravi K"))
        .andExpect(jsonPath("$.timezone").value("Asia/Dubai"))
        .andExpect(jsonPath("$.period_start_day").value(25))
        .andExpect(jsonPath("$.dues_digest_enabled").value(false));
  }

  @Test
  void fieldsLeftOutOfAPatchStayUnchanged() throws Exception {
    Session s = fixtures.signup();

    patchMe(
            s,
            """
            {"period_start_day": 15}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period_start_day").value(15))
        .andExpect(jsonPath("$.name").value("Test"))
        .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
        .andExpect(jsonPath("$.dues_digest_enabled").value(true));
  }

  @Test
  void anUnknownTimezoneGets422AndChangesNothing() throws Exception {
    Session s = fixtures.signup();

    patchMe(
            s,
            """
            {"timezone": "Mars/Olympus", "name": "Changed"}
            """)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_timezone"));

    mockMvc
        .perform(get("/api/v1/me").cookie(new Cookie("hisaab_access", s.accessToken())))
        .andExpect(jsonPath("$.name").value("Test"));
  }

  @Test
  void periodStartDayOutsideOneToTwentyEightGets400() throws Exception {
    Session s = fixtures.signup();

    for (int day : new int[] {0, 29}) {
      patchMe(s, "{\"period_start_day\": %d}".formatted(day))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("validation_failed"))
          .andExpect(jsonPath("$.error.details.fields.period_start_day").exists());
    }
  }

  @Test
  void anEmptyNameGets400() throws Exception {
    patchMe(
            fixtures.signup(),
            """
            {"name": ""}
            """)
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchWithoutATokenGets401() throws Exception {
    mockMvc
        .perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions patchMe(Session s, String json) throws Exception {
    return mockMvc.perform(
        patch("/api/v1/me")
            .cookie(new Cookie("hisaab_access", s.accessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }
}
