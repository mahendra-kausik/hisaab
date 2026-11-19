package in.hisaab.dues;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of {@code dues}. created_at is not mapped; the database fills it. Party and direction
 * never change after creation, so they have no setter.
 */
@Entity
@Table(name = "dues")
public class Due {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private UUID partyId;
  private DueDirection direction;
  private long amountPaise;
  private String reason;
  private LocalDate dueDate;
  private UUID projectId;
  private UUID categoryId;
  private UUID originTransactionId;
  private Instant cancelledAt;
  private UUID createdBy;
  private Instant updatedAt;
  private Instant deletedAt;

  protected Due() {}

  public Due(
      UUID workspaceId,
      UUID createdBy,
      UUID partyId,
      DueDirection direction,
      long amountPaise,
      String reason,
      LocalDate dueDate,
      UUID projectId,
      UUID categoryId,
      UUID originTransactionId,
      Instant now) {
    this.workspaceId = workspaceId;
    this.createdBy = createdBy;
    this.partyId = partyId;
    this.direction = direction;
    this.amountPaise = amountPaise;
    this.reason = reason;
    this.dueDate = dueDate;
    this.projectId = projectId;
    this.categoryId = categoryId;
    this.originTransactionId = originTransactionId;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPartyId() {
    return partyId;
  }

  public DueDirection getDirection() {
    return direction;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public String getReason() {
    return reason;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public UUID getOriginTransactionId() {
    return originTransactionId;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** PUT semantics: every editable field is overwritten, so a null clears the optional ones. */
  public void replace(
      long amountPaise,
      String reason,
      LocalDate dueDate,
      UUID projectId,
      UUID categoryId,
      Instant now) {
    this.amountPaise = amountPaise;
    this.reason = reason;
    this.dueDate = dueDate;
    this.projectId = projectId;
    this.categoryId = categoryId;
    this.updatedAt = now;
  }

  public void setCancelled(boolean cancelled, Instant now) {
    // Cancelling an already cancelled due keeps the original cancel time.
    if (cancelled && cancelledAt == null) {
      cancelledAt = now;
    } else if (!cancelled) {
      cancelledAt = null;
    }
    this.updatedAt = now;
  }
}
