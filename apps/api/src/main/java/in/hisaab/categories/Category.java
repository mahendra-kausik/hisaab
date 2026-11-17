package in.hisaab.categories;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code categories}. Kind and parent never change after creation; only the name and the
 * hidden flag can.
 */
@Entity
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private UUID parentId;
  private String name;
  private CategoryKind kind;
  private boolean isSystem;
  private Instant hiddenAt;
  private int sortOrder;

  protected Category() {}

  public Category(
      UUID workspaceId,
      UUID parentId,
      String name,
      CategoryKind kind,
      boolean isSystem,
      int sortOrder) {
    this.workspaceId = workspaceId;
    this.parentId = parentId;
    this.name = name;
    this.kind = kind;
    this.isSystem = isSystem;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getParentId() {
    return parentId;
  }

  public String getName() {
    return name;
  }

  public CategoryKind getKind() {
    return kind;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public Instant getHiddenAt() {
    return hiddenAt;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void setHidden(boolean hidden, Instant now) {
    if (hidden && hiddenAt == null) {
      hiddenAt = now;
    } else if (!hidden) {
      hiddenAt = null;
    }
  }
}
