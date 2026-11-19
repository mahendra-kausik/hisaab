package in.hisaab.projects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row of {@code projects}. created_at is not mapped; the database fills it. */
@Entity
@Table(name = "projects")
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private String name;
  private String notes;
  private Instant archivedAt;
  private Instant updatedAt;

  protected Project() {}

  public Project(UUID workspaceId, String name, String notes, Instant now) {
    this.workspaceId = workspaceId;
    this.name = name;
    this.notes = notes;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void update(String name, String notes, Instant now) {
    this.name = name;
    this.notes = notes;
    this.updatedAt = now;
  }

  public void setArchived(boolean archived, Instant now) {
    // Archiving an already archived project keeps the original archive time.
    if (archived && archivedAt == null) {
      archivedAt = now;
    } else if (!archived) {
      archivedAt = null;
    }
    this.updatedAt = now;
  }
}
