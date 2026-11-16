package in.hisaab.workspaces;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring generates the implementation (save, findById, ...) at startup from this interface. */
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  // JPQL (queries entities, not tables). The entities hold plain id fields rather than
  // object links, so the join is written out by hand. `select new` builds the record directly.
  @Query(
      """
      select new in.hisaab.workspaces.WorkspaceMembership(w.id, w.name, w.kind, m.role)
      from Workspace w, WorkspaceMember m
      where m.workspaceId = w.id and m.userId = :userId
      order by w.name, w.id
      """)
  List<WorkspaceMembership> findMemberships(UUID userId);
}
