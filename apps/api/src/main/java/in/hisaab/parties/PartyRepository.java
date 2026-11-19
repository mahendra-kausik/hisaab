package in.hisaab.parties;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, UUID> {

  List<Party> findByWorkspaceIdOrderByName(UUID workspaceId);

  Optional<Party> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
