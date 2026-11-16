package in.hisaab.auth;

import static in.hisaab.TestFixtures.from;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.hisaab.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Requests send an empty JSON body, so they fail validation (400) in microseconds without hashing a
 * password; the rate limiter runs before validation and still counts each one. Every test uses its
 * own IP address because the counters are shared by the whole test run.
 */
class AuthRateLimitTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;

  @Test
  void theEleventhLoginFromOneIpWithinAMinuteGets429() throws Exception {
    for (int i = 0; i < 10; i++) {
      attempt("/api/v1/auth/login", "192.0.2.10").andExpect(status().isBadRequest());
    }

    attempt("/api/v1/auth/login", "192.0.2.10")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error.code").value("rate_limited"));
  }

  @Test
  void anotherIpIsNotAffected() throws Exception {
    for (int i = 0; i < 11; i++) {
      attempt("/api/v1/auth/login", "192.0.2.20");
    }

    attempt("/api/v1/auth/login", "192.0.2.21").andExpect(status().isBadRequest());
  }

  @Test
  void theSixthSignupFromOneIpGets429() throws Exception {
    for (int i = 0; i < 5; i++) {
      attempt("/api/v1/auth/signup", "192.0.2.30").andExpect(status().isBadRequest());
    }

    attempt("/api/v1/auth/signup", "192.0.2.30").andExpect(status().isTooManyRequests());
  }

  @Test
  void loginAndSignupHaveSeparateBuckets() throws Exception {
    for (int i = 0; i < 5; i++) {
      attempt("/api/v1/auth/signup", "192.0.2.40");
    }

    attempt("/api/v1/auth/login", "192.0.2.40").andExpect(status().isBadRequest());
  }

  @Test
  void otherEndpointsAreNeverThrottled() throws Exception {
    for (int i = 0; i < 30; i++) {
      mockMvc.perform(get("/api/v1/health").with(from("192.0.2.50"))).andExpect(status().isOk());
    }
  }

  private ResultActions attempt(String path, String ip) throws Exception {
    return mockMvc.perform(
        post(path).with(from(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"));
  }
}
