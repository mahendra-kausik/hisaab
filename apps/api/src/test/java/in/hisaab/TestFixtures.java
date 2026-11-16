package in.hisaab;

import in.hisaab.common.security.CurrentUser;
import in.hisaab.workspaces.WorkspaceKind;
import in.hisaab.workspaces.WorkspaceService;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates real users with their own personal workspace. The Postgres container is shared by every
 * test class, so each user gets a random email and tests only ever look at their own users' data.
 */
@TestComponent
public class TestFixtures {

  public record TestUser(UUID id, UUID workspaceId) {}

  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final WorkspaceService workspaceService;

  public TestFixtures(
      JdbcTemplate jdbc, CurrentUser currentUser, WorkspaceService workspaceService) {
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.workspaceService = workspaceService;
  }

  public TestUser createUser() {
    // `users` has no RLS, so a plain insert works. The real signup flow arrives in U2.
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
}
