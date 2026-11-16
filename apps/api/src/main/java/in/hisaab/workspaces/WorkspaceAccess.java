package in.hisaab.workspaces;

import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.ForbiddenException;
import in.hisaab.common.web.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The app-level membership check: the first line of every workspace-scoped service method.
 * (Postgres RLS is the second line of defence if one is ever forgotten.)
 */
@Component
public class WorkspaceAccess {

  private final WorkspaceMemberRepository members;
  private final CurrentUser currentUser;

  public WorkspaceAccess(WorkspaceMemberRepository members, CurrentUser currentUser) {
    this.members = members;
    this.currentUser = currentUser;
  }

  /**
   * Passes if the current user belongs to the workspace with at least {@code role}.
   *
   * @throws NotFoundException if they are not a member (404, so existence is not revealed)
   * @throws ForbiddenException if they are a member with too weak a role
   */
  // Default propagation: joins the caller's transaction, or starts one if there is none.
  @Transactional(readOnly = true)
  public void require(UUID workspaceId, WorkspaceRole role) {
    var member =
        members
            .findById(new WorkspaceMemberId(workspaceId, currentUser.require()))
            .orElseThrow(() -> new NotFoundException("Workspace not found"));
    if (!member.getRole().includes(role)) {
      throw new ForbiddenException("Your role does not allow this");
    }
  }
}
