package in.hisaab.workspaces;

import in.hisaab.accounts.AccountService;
import in.hisaab.categories.CategoryService;
import in.hisaab.common.security.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

  private final WorkspaceRepository workspaces;
  private final WorkspaceMemberRepository members;
  private final CurrentUser currentUser;
  private final AccountService accountService;
  private final CategoryService categoryService;

  public WorkspaceService(
      WorkspaceRepository workspaces,
      WorkspaceMemberRepository members,
      CurrentUser currentUser,
      AccountService accountService,
      CategoryService categoryService) {
    this.workspaces = workspaces;
    this.members = members;
    this.currentUser = currentUser;
    this.accountService = accountService;
    this.categoryService = categoryService;
  }

  /**
   * Creates a workspace owned by the current user, with its default Cash account and categories.
   * Signup calls this for the personal workspace; POST /workspaces calls it for any other.
   */
  // One transaction: the workspace, its owner membership and its default data are saved together
  // or not at all.
  @Transactional
  public Workspace create(String name, WorkspaceKind kind) {
    var userId = currentUser.require();
    var workspace = workspaces.save(new Workspace(name, kind, userId));
    // Flush so the membership row exists before the seed inserts below: their RLS policy
    // checks that the current user is a member of the workspace.
    members.saveAndFlush(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER));
    accountService.createDefaults(workspace.getId());
    categoryService.createDefaults(workspace.getId(), kind);
    return workspace;
  }

  /** The workspaces the current user belongs to. RLS already hides everyone else's. */
  @Transactional(readOnly = true)
  public List<WorkspaceMembership> listForCurrentUser() {
    return workspaces.findMemberships(currentUser.require());
  }
}
