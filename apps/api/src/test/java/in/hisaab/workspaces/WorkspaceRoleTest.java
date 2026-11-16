package in.hisaab.workspaces;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Plain unit test: no Spring, no database. */
class WorkspaceRoleTest {

  @Test
  void strongerRolesIncludeWeakerOnes() {
    assertThat(WorkspaceRole.OWNER.includes(WorkspaceRole.EDITOR)).isTrue();
    assertThat(WorkspaceRole.EDITOR.includes(WorkspaceRole.VIEWER)).isTrue();
    assertThat(WorkspaceRole.VIEWER.includes(WorkspaceRole.VIEWER)).isTrue();
  }

  @Test
  void weakerRolesDoNotIncludeStrongerOnes() {
    assertThat(WorkspaceRole.VIEWER.includes(WorkspaceRole.EDITOR)).isFalse();
    assertThat(WorkspaceRole.EDITOR.includes(WorkspaceRole.OWNER)).isFalse();
  }
}
