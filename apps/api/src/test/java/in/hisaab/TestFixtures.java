package in.hisaab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.hisaab.common.security.CurrentUser;
import in.hisaab.workspaces.WorkspaceKind;
import in.hisaab.workspaces.WorkspaceService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Creates real users with their own personal workspace. The Postgres container is shared by every
 * test class, so each user gets a random email and tests only ever look at their own users' data.
 */
@TestComponent
public class TestFixtures {

  public static final String PASSWORD = "correct horse battery";

  public record TestUser(UUID id, UUID workspaceId) {}

  /** A user created through the real signup endpoint, with the tokens it handed out. */
  public record Session(String email, String accessToken, String refreshToken) {}

  // Login and signup are rate limited per client IP, and the app (with its counters) is shared
  // by every test. Giving each request its own fake IP keeps tests from throttling each other.
  private static final AtomicInteger NEXT_IP = new AtomicInteger();

  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final WorkspaceService workspaceService;
  private final MockMvc mockMvc;

  public TestFixtures(
      JdbcTemplate jdbc,
      CurrentUser currentUser,
      WorkspaceService workspaceService,
      MockMvc mockMvc) {
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.workspaceService = workspaceService;
    this.mockMvc = mockMvc;
  }

  public TestUser createUser() {
    // `users` has no RLS, so a plain insert works. Use signup() for the full flow.
    UUID userId =
        jdbc.queryForObject(
            "INSERT INTO users (email, password_hash, name) VALUES (?, 'not-a-real-hash', 'Test')"
                + " RETURNING id",
            UUID.class,
            UUID.randomUUID() + "@example.com");
    UUID workspaceId =
        currentUser.runAs(
            userId, () -> workspaceService.create("Personal", WorkspaceKind.PERSONAL).getId());
    return new TestUser(userId, workspaceId);
  }

  /** Signs up a brand new user through the real endpoint. */
  public Session signup() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    var response =
        mockMvc
            .perform(
                post("/api/v1/auth/signup")
                    .with(fromNewIp())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "password": "%s", "name": "Test"}
                        """
                            .formatted(email, PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    return new Session(
        email,
        response.getCookie("hisaab_access").getValue(),
        response.getCookie("hisaab_refresh").getValue());
  }

  /** Makes the request look like it came from an IP address no other request has used. */
  public static RequestPostProcessor fromNewIp() {
    int n = NEXT_IP.incrementAndGet();
    return from("10.1.%d.%d".formatted(n / 256, n % 256));
  }

  public static RequestPostProcessor from(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }
}
