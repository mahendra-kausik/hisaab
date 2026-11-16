package in.hisaab.auth;

import static in.hisaab.TestFixtures.PASSWORD;
import static in.hisaab.TestFixtures.fromNewIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AuthFlowTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;
  @Autowired private JdbcTemplate jdbc;

  // ---- signup ----

  @Test
  void signupCreatesUserWithPersonalWorkspaceAndHttpOnlyCookies() throws Exception {
    String email = UUID.randomUUID() + "@example.com";

    var response =
        signup("Alice-" + email.toUpperCase(), PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("alice-" + email))
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
            .andExpect(jsonPath("$.period_start_day").value(1))
            .andExpect(jsonPath("$.dues_digest_enabled").value(true))
            .andExpect(jsonPath("$.password_hash").doesNotExist())
            .andExpect(jsonPath("$.workspaces.length()").value(1))
            .andExpect(jsonPath("$.workspaces[0].name").value("Personal"))
            .andExpect(jsonPath("$.workspaces[0].kind").value("personal"))
            .andExpect(jsonPath("$.workspaces[0].role").value("owner"))
            .andReturn()
            .getResponse();

    var cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(cookies).hasSize(2);
    assertThat(cookies)
        .anySatisfy(c ->
            assertThat(c)
                .startsWith("hisaab_access=")
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=900"));
    assertThat(cookies)
        .anySatisfy(c ->
            assertThat(c)
                .startsWith("hisaab_refresh=")
                .contains(
                    "HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=2592000"));

    // The stored password is a bcrypt hash, never the password itself.
    String stored =
        jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE email = ?", String.class, "alice-" + email);
    assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
  }

  @Test
  void signupWithAnExistingEmailGets409EvenInADifferentCase() throws Exception {
    Session existing = fixtures.signup();

    signup(existing.email().toUpperCase(), PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("email_taken"));
  }

  @Test
  void signupWithInvalidInputGets400WithFieldErrors() throws Exception {
    signup("not-an-email", "short")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("validation_failed"))
        .andExpect(jsonPath("$.error.details.fields.email").exists())
        .andExpect(jsonPath("$.error.details.fields.password").exists());
  }

  @Test
  void signupRejectsPasswordsLongerThanBcryptCanUse() throws Exception {
    // 40 characters, but 80 bytes in UTF-8: passes @Size, fails the byte check.
    signup(UUID.randomUUID() + "@example.com", "é".repeat(40))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("password_too_long"));
  }

  // ---- login ----

  @Test
  void loginWithTheRightPasswordReturnsProfileAndCookies() throws Exception {
    Session s = fixtures.signup();

    var response =
        login(s.email().toUpperCase(), PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(s.email()))
            .andExpect(jsonPath("$.workspaces.length()").value(1))
            .andReturn()
            .getResponse();

    assertThat(response.getCookie("hisaab_access")).isNotNull();
    assertThat(response.getCookie("hisaab_refresh")).isNotNull();
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
    Session s = fixtures.signup();

    String wrongPassword =
        login(s.email(), "not the password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("invalid_credentials"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownEmail =
        login(UUID.randomUUID() + "@example.com", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(unknownEmail).isEqualTo(wrongPassword);
  }

  // ---- /me and access tokens ----

  @Test
  void meWithoutATokenGets401InTheErrorShape() throws Exception {
    mockMvc
        .perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("unauthorized"));
  }

  @Test
  void meAcceptsTheAccessTokenAsABearerHeaderToo() throws Exception {
    Session s = fixtures.signup();

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + s.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(s.email()));
  }

  @Test
  void meRejectsATamperedToken() throws Exception {
    Session s = fixtures.signup();

    mockMvc
        .perform(get("/api/v1/me").cookie(new Cookie("hisaab_access", s.accessToken() + "x")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void eachUserSeesOnlyTheirOwnWorkspace() throws Exception {
    Session a = fixtures.signup();
    Session b = fixtures.signup();

    String idA = workspaceIdSeenBy(a);
    String idB = workspaceIdSeenBy(b);

    assertThat(idA).isNotEqualTo(idB);
  }

  // ---- refresh ----

  @Test
  void refreshRotatesTheTokenAndReuseRevokesTheWholeFamily() throws Exception {
    Session s = fixtures.signup();

    MockHttpServletResponse first =
        refresh(s.refreshToken()).andExpect(status().isNoContent()).andReturn().getResponse();
    String second = first.getCookie("hisaab_refresh").getValue();
    assertThat(second).isNotEqualTo(s.refreshToken());
    // The new access token works.
    mockMvc
        .perform(get("/api/v1/me").cookie(first.getCookie("hisaab_access")))
        .andExpect(status().isOk());

    // Replaying the first token is reuse: rejected, and it burns the whole family...
    refresh(s.refreshToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("invalid_refresh_token"));
    // ...including the legitimate newer token.
    refresh(second).andExpect(status().isUnauthorized());
  }

  @Test
  void anExpiredRefreshTokenGets401() throws Exception {
    Session s = fixtures.signup();
    jdbc.update(
        "UPDATE refresh_tokens SET expires_at = now() - interval '1 day'"
            + " WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        s.email());

    refresh(s.refreshToken()).andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithoutACookieOrWithAnUnknownTokenGets401() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("invalid_refresh_token"));
    refresh("never-issued").andExpect(status().isUnauthorized());
  }

  @Test
  void refreshAndLoginIgnoreAnExpiredOrGarbageAccessCookie() throws Exception {
    Session s = fixtures.signup();
    Cookie garbage = new Cookie("hisaab_access", "garbage.not.ajwt");

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(garbage, new Cookie("hisaab_refresh", s.refreshToken())))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(fromNewIp())
                .cookie(garbage)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(s.email(), PASSWORD)))
        .andExpect(status().isOk());
  }

  // ---- logout ----

  @Test
  void logoutRevokesTheSessionAndClearsTheCookies() throws Exception {
    Session s = fixtures.signup();

    var response =
        mockMvc
            .perform(
                post("/api/v1/auth/logout").cookie(new Cookie("hisaab_refresh", s.refreshToken())))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(2)
        .allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
    refresh(s.refreshToken()).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithoutACookieStillSucceeds() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
  }

  // ---- helpers ----

  private ResultActions signup(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/signup")
            .with(fromNewIp())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "password": "%s", "name": "Alice"}
                """
                    .formatted(email, password)));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .with(fromNewIp())
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, password)));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/refresh").cookie(new Cookie("hisaab_refresh", refreshToken)));
  }

  private String workspaceIdSeenBy(Session s) throws Exception {
    var json =
        mockMvc
            .perform(get("/api/v1/me").cookie(new Cookie("hisaab_access", s.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspaces.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return com.jayway.jsonpath.JsonPath.read(json, "$.workspaces[0].id");
  }

  private static String credentials(String email, String password) {
    return """
    {"email": "%s", "password": "%s"}
    """
        .formatted(email, password);
  }
}
