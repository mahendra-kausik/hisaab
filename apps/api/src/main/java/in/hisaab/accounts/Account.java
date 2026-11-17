package in.hisaab.accounts;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row of {@code accounts}. created_at is not mapped; the database fills it. */
@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private String name;
  private AccountType type;
  private long openingBalancePaise;
  private Instant archivedAt;
  private Instant updatedAt;

  protected Account() {}

  public Account(
      UUID workspaceId, String name, AccountType type, long openingBalancePaise, Instant now) {
    this.workspaceId = workspaceId;
    this.name = name;
    this.type = type;
    this.openingBalancePaise = openingBalancePaise;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getName() {
    return name;
  }

  public AccountType getType() {
    return type;
  }

  public long getOpeningBalancePaise() {
    return openingBalancePaise;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void update(String name, AccountType type, long openingBalancePaise, Instant now) {
    this.name = name;
    this.type = type;
    this.openingBalancePaise = openingBalancePaise;
    this.updatedAt = now;
  }

  public void setArchived(boolean archived, Instant now) {
    // Archiving an already archived account keeps the original archive time.
    if (archived && archivedAt == null) {
      archivedAt = now;
    } else if (!archived) {
      archivedAt = null;
    }
    this.updatedAt = now;
  }
}
