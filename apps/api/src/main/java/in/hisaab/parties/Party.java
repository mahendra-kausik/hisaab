package in.hisaab.parties;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row of {@code parties}. created_at is not mapped; the database fills it. */
@Entity
@Table(name = "parties")
public class Party {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private String name;
  private String phone;
  private PartyRole role;
  private String notes;
  private Instant archivedAt;
  private Instant updatedAt;

  protected Party() {}

  public Party(
      UUID workspaceId, String name, String phone, PartyRole role, String notes, Instant now) {
    this.workspaceId = workspaceId;
    this.name = name;
    this.phone = phone;
    this.role = role;
    this.notes = notes;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  public PartyRole getRole() {
    return role;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void update(String name, String phone, PartyRole role, String notes, Instant now) {
    this.name = name;
    this.phone = phone;
    this.role = role;
    this.notes = notes;
    this.updatedAt = now;
  }

  public void setArchived(boolean archived, Instant now) {
    // Archiving an already archived party keeps the original archive time.
    if (archived && archivedAt == null) {
      archivedAt = now;
    } else if (!archived) {
      archivedAt = null;
    }
    this.updatedAt = now;
  }
}
