package in.hisaab.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code refresh_tokens}. Only the SHA-256 hash of the token is stored, never the token
 * itself, so a database leak does not hand out working sessions.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID userId;
  private String tokenHash;
  private UUID familyId;
  private Instant expiresAt;
  private Instant revokedAt;

  protected RefreshToken() {}

  public RefreshToken(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void revoke(Instant now) {
    this.revokedAt = now;
  }
}
