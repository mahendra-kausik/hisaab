package in.hisaab.workspaces;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

/** One row of {@code workspace_members}: which user belongs to which workspace, and as what. */
@Entity
@Table(name = "workspace_members")
@IdClass(WorkspaceMemberId.class)
public class WorkspaceMember {

  @Id private UUID workspaceId;
  @Id private UUID userId;

  private WorkspaceRole role;

  protected WorkspaceMember() {}

  public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
    this.workspaceId = workspaceId;
    this.userId = userId;
    this.role = role;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserId() {
    return userId;
  }

  public WorkspaceRole getRole() {
    return role;
  }
}
