package in.hisaab.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  // PESSIMISTIC_WRITE adds FOR UPDATE: the row is locked until the transaction ends, so two
  // simultaneous refreshes with the same token take turns instead of both succeeding.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  // @Modifying marks a query that changes data (UPDATE/DELETE) rather than reading it.
  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.familyId = :familyId and t.revokedAt is null")
  void revokeFamily(UUID familyId, Instant now);
}
