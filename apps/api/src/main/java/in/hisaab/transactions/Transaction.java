package in.hisaab.transactions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of {@code transactions}. created_at is not mapped; the database fills it (and the list
 * query reads it). source, raw_description and merchant are not mapped yet: manual transactions use
 * the column defaults, and Phase 2 adds what it needs. A transfer is two of these rows (see {@link
 * #transferLeg}) that share a transfer_group_id.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private UUID accountId;
  private TransactionType type;
  private long amountPaise;
  private LocalDate occurredOn;
  private UUID categoryId;
  private UUID partyId;
  private UUID projectId;
  private UUID dueId;
  private PaymentMethod paymentMethod;
  private String note;
  private UUID transferGroupId;
  private UUID createdBy;
  private Instant updatedAt;
  private Instant deletedAt;

  protected Transaction() {}

  public Transaction(
      UUID workspaceId,
      UUID createdBy,
      UUID accountId,
      TransactionType type,
      long amountPaise,
      LocalDate occurredOn,
      UUID categoryId,
      UUID partyId,
      UUID projectId,
      UUID dueId,
      PaymentMethod paymentMethod,
      String note,
      Instant now) {
    this.workspaceId = workspaceId;
    this.createdBy = createdBy;
    this.accountId = accountId;
    this.type = type;
    this.amountPaise = amountPaise;
    this.occurredOn = occurredOn;
    this.categoryId = categoryId;
    this.partyId = partyId;
    this.projectId = projectId;
    this.dueId = dueId;
    this.paymentMethod = paymentMethod;
    this.note = note;
    this.updatedAt = now;
  }

  /** One side of a transfer: no category, and the shared group id ties it to the other side. */
  public static Transaction transferLeg(
      UUID workspaceId,
      UUID createdBy,
      UUID transferGroupId,
      UUID accountId,
      TransactionType type,
      long amountPaise,
      LocalDate occurredOn,
      PaymentMethod paymentMethod,
      String note,
      Instant now) {
    var leg =
        new Transaction(
            workspaceId,
            createdBy,
            accountId,
            type,
            amountPaise,
            occurredOn,
            null,
            null,
            null,
            null,
            paymentMethod,
            note,
            now);
    leg.transferGroupId = transferGroupId;
    return leg;
  }

  public UUID getId() {
    return id;
  }

  public TransactionType getType() {
    return type;
  }

  public UUID getTransferGroupId() {
    return transferGroupId;
  }

  public boolean isTransfer() {
    return transferGroupId != null;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getPartyId() {
    return partyId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getDueId() {
    return dueId;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  /** PUT semantics: every editable field is overwritten, so a null clears the optional ones. */
  public void replace(
      UUID accountId,
      TransactionType type,
      long amountPaise,
      LocalDate occurredOn,
      UUID categoryId,
      UUID partyId,
      UUID projectId,
      UUID dueId,
      PaymentMethod paymentMethod,
      String note,
      Instant now) {
    this.accountId = accountId;
    this.type = type;
    this.amountPaise = amountPaise;
    this.occurredOn = occurredOn;
    this.categoryId = categoryId;
    this.partyId = partyId;
    this.projectId = projectId;
    this.dueId = dueId;
    this.paymentMethod = paymentMethod;
    this.note = note;
    this.updatedAt = now;
  }

  public void softDelete(Instant now) {
    this.deletedAt = now;
    this.updatedAt = now;
  }

  public void restore(Instant now) {
    this.deletedAt = null;
    this.updatedAt = now;
  }
}
