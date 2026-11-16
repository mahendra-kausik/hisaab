package in.hisaab.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository
    extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {}
