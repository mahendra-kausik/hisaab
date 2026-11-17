package in.hisaab.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring derives these queries from the method names. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

  List<Account> findByWorkspaceIdOrderByName(UUID workspaceId);

  Optional<Account> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
