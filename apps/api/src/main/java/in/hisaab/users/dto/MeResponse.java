package in.hisaab.users.dto;

import in.hisaab.users.User;
import java.util.List;
import java.util.UUID;

/** The signed-in user's profile, settings, and the workspaces they belong to. */
public record MeResponse(
    UUID id,
    String email,
    String name,
    String timezone,
    int periodStartDay,
    boolean duesDigestEnabled,
    List<WorkspaceSummary> workspaces) {

  public record WorkspaceSummary(UUID id, String name, String kind, String role) {}

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
