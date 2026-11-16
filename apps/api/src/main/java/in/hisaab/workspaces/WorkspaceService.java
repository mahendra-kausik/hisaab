package in.hisaab.workspaces;

import in.hisaab.common.security.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

  private final WorkspaceRepository workspaces;
  private final WorkspaceMemberRepository members;
  private final CurrentUser currentUser;

  public WorkspaceService(
      WorkspaceRepository workspaces, WorkspaceMemberRepository members, CurrentUser currentUser) {
    this.workspaces = workspaces;
    this.members = members;
    this.currentUser = currentUser;
  }

  /** Creates a workspace owned by the current user. Signup (U2) calls this for the personal one. */
  // One transaction: the workspace and its owner membership are saved together or not at all.
  @Transactional
  public Workspace create(String name, WorkspaceKind kind) {
    var userId = currentUser.require();
    var workspace = workspaces.save(new Workspace(name, kind, userId));
    members.save(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER));
    return workspace;
  }

  /** The workspaces the current user belongs to. RLS already hides everyone else's. */
  @Transactional(readOnly = true)
  public List<WorkspaceMembership> listForCurrentUser() {
    return workspaces.findMemberships(currentUser.require());
  }
}
