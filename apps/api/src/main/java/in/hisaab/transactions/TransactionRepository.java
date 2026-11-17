package in.hisaab.transactions;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Writes go through JPA; the list and read queries are in {@link TransactionQueries}. */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  /** Includes soft-deleted rows; restore needs to find them. */
  Optional<Transaction> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
