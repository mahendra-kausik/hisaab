package in.hisaab.workspaces;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring generates the implementation (save, findById, ...) at startup from this interface. */
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {}
