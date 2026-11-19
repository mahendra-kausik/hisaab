package in.hisaab.recurring;

import in.hisaab.transactions.TransactionType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of {@code recurring_rules}. created_at is filled by the database. */
@Entity
@Table(name = "recurring_rules")
public class RecurringRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private UUID accountId;
  private TransactionType type;
  private long amountPaise;
  private UUID categoryId;
  private UUID partyId;
  private UUID projectId;
  private String note;
  private Frequency frequency;
  private int intervalCount;
  private LocalDate anchorDate;
  private LocalDate nextRunOn;
  private LocalDate endsOn;
  private Instant pausedAt;
  private UUID createdBy;
  private Instant updatedAt;

  protected RecurringRule() {}

  public RecurringRule(UUID workspaceId, UUID createdBy) {
    this.workspaceId = workspaceId;
    this.createdBy = createdBy;
  }

  /** PUT semantics: every editable field is overwritten. {@code nextRunOn} is set by the caller. */
  public void replace(
      UUID accountId,
      TransactionType type,
      long amountPaise,
      UUID categoryId,
      UUID partyId,
      UUID projectId,
      String note,
      Frequency frequency,
      int intervalCount,
      LocalDate anchorDate,
      LocalDate endsOn,
      Instant now) {
    this.accountId = accountId;
    this.type = type;
    this.amountPaise = amountPaise;
    this.categoryId = categoryId;
    this.partyId = partyId;
    this.projectId = projectId;
    this.note = note;
    this.frequency = frequency;
    this.intervalCount = intervalCount;
    this.anchorDate = anchorDate;
    this.endsOn = endsOn;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public TransactionType getType() {
    return type;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public UUID getPartyId() {
    return partyId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getNote() {
    return note;
  }

  public Frequency getFrequency() {
    return frequency;
  }

  public int getIntervalCount() {
    return intervalCount;
  }

  public LocalDate getAnchorDate() {
    return anchorDate;
  }

  public LocalDate getNextRunOn() {
    return nextRunOn;
  }

  public void setNextRunOn(LocalDate nextRunOn) {
    this.nextRunOn = nextRunOn;
  }

  public LocalDate getEndsOn() {
    return endsOn;
  }

  public Instant getPausedAt() {
    return pausedAt;
  }

  public void setPausedAt(Instant pausedAt, Instant now) {
    this.pausedAt = pausedAt;
    this.updatedAt = now;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public boolean isPaused() {
    return pausedAt != null;
  }
}
