package in.hisaab.workspaces;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A JPA entity: one row of the {@code workspaces} table. created_at/updated_at are not mapped yet;
 * the database fills them from its defaults.
 */
@Entity
@Table(name = "workspaces")
public class Workspace {

  // IDENTITY: the database generates the id (gen_random_uuid()) and Hibernate reads it back.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private String name;
  private WorkspaceKind kind;
  private UUID createdBy;

  // JPA needs a no-argument constructor to build entities from rows.
  protected Workspace() {}

  public Workspace(String name, WorkspaceKind kind, UUID createdBy) {
    this.name = name;
    this.kind = kind;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public WorkspaceKind getKind() {
    return kind;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
