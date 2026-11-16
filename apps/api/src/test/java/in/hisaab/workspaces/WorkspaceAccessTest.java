package in.hisaab.workspaces;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.TestUser;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceAccessTest extends PostgresTestBase {

  @Autowired private WorkspaceAccess access;
  @Autowired private CurrentUser currentUser;
  @Autowired private TestFixtures fixtures;

  @Test
  void memberPassesTheCheck() {
    TestUser a = fixtures.createUser();

    assertThatCode(() ->
            currentUser.runAs(a.id(), () -> access.require(a.workspaceId(), WorkspaceRole.OWNER)))
        .doesNotThrowAnyException();
  }

  @Test
  void nonMemberGets404() {
    TestUser a = fixtures.createUser();
    TestUser b = fixtures.createUser();

    assertThatThrownBy(() ->
            currentUser.runAs(a.id(), () -> access.require(b.workspaceId(), WorkspaceRole.VIEWER)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void unknownWorkspaceLooksTheSameAsSomeoneElsesWorkspace() {
    TestUser a = fixtures.createUser();

    assertThatThrownBy(() ->
            currentUser.runAs(
                a.id(), () -> access.require(UUID.randomUUID(), WorkspaceRole.VIEWER)))
        .isInstanceOf(NotFoundException.class);
  }
}
