package in.hisaab.recurring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {

  List<RecurringRule> findByWorkspaceIdOrderByNextRunOnAsc(UUID workspaceId);

  Optional<RecurringRule> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
