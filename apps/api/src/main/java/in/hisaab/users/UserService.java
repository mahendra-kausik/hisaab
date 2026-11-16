package in.hisaab.users;

import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.UnauthorizedException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.users.dto.MeResponse;
import in.hisaab.users.dto.UpdateSettingsRequest;
import in.hisaab.workspaces.WorkspaceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;
  private final WorkspaceService workspaceService;
  private final CurrentUser currentUser;
  private final Clock clock;

  public UserService(
      UserRepository users,
      WorkspaceService workspaceService,
      CurrentUser currentUser,
      Clock clock) {
    this.users = users;
    this.workspaceService = workspaceService;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Joins the caller's transaction (signup runs this and the workspace insert as one unit). */
  @Transactional
  public User create(UUID id, String email, String passwordHash, String name) {
    return users.save(new User(id, email, passwordHash, name, Instant.now(clock)));
  }

  /** Expects an already lowercased email. {@code users} has no RLS, so no current user needed. */
  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return users.findByEmail(email);
  }

  /** The current user's profile and workspaces. */
  @Transactional(readOnly = true)
  public MeResponse me() {
    return toResponse(load());
  }

  @Transactional
  public MeResponse updateSettings(UpdateSettingsRequest patch) {
    var user = load();
    if (patch.timezone() != null && !ZoneId.getAvailableZoneIds().contains(patch.timezone())) {
      throw new ValidationException(
          "invalid_timezone",
          "Unknown timezone",
          Map.of("fields", Map.of("timezone", "Use an IANA name such as Asia/Kolkata")));
    }
    // Null means "not in the request": keep the current value.
    user.updateSettings(
        patch.name() != null ? patch.name() : user.getName(),
        patch.timezone() != null ? patch.timezone() : user.getTimezone(),
        patch.periodStartDay() != null ? patch.periodStartDay() : user.getPeriodStartDay(),
        patch.duesDigestEnabled() != null ? patch.duesDigestEnabled() : user.isDuesDigestEnabled(),
        Instant.now(clock));
    // `user` is a managed entity: Hibernate writes the changes when the transaction commits.
    return toResponse(user);
  }

  private User load() {
    // A valid token whose user no longer exists (account deleted) is simply unauthenticated.
    return users
        .findById(currentUser.require())
        .orElseThrow(() -> new UnauthorizedException("unauthorized", "Sign in again"));
  }

  private MeResponse toResponse(User user) {
    var workspaces =
        workspaceService.listForCurrentUser().stream()
            .map(w ->
                new MeResponse.WorkspaceSummary(
                    w.id(), w.name(), w.kind().name().toLowerCase(), w.role().name().toLowerCase()))
            .toList();
    return MeResponse.from(user, workspaces);
  }
}
