package in.hisaab.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.hisaab.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class HealthControllerTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthReturnsOkAfterARealDatabaseRoundTrip() throws Exception {
    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.db").value("ok"))
        .andExpect(jsonPath("$.ok").doesNotExist());
  }
}
