package in.hisaab.users.dto;

import in.hisaab.users.User;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** The signed-in user's profile, settings, and the workspaces they belong to. */
// @NotNull tells springdoc every field is always present, so the generated TypeScript type has
// them as required (not optional).
public record MeResponse(
    @NotNull UUID id,
    @NotNull String email,
    @NotNull String name,
    @NotNull String timezone,
    @NotNull int periodStartDay,
    @NotNull boolean duesDigestEnabled,
    @NotNull List<WorkspaceSummary> workspaces) {

  public record WorkspaceSummary(
      @NotNull UUID id, @NotNull String name, @NotNull String kind, @NotNull String role) {}

  public static MeResponse from(User user, List<WorkspaceSummary> workspaces) {
    return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getTimezone(),
        user.getPeriodStartDay(),
        user.isDuesDigestEnabled(),
        workspaces);
  }
}
