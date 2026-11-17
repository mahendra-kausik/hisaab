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
 * query reads it). Transfer, source, raw_description and merchant columns are not mapped yet:
 * manual transactions use the column defaults, and U5 / Phase 2 add what they need.
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
  private PaymentMethod paymentMethod;
  private String note;
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
    this.paymentMethod = paymentMethod;
    this.note = note;
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
      PaymentMethod paymentMethod,
      String note,
      Instant now) {
    this.accountId = accountId;
    this.type = type;
    this.amountPaise = amountPaise;
    this.occurredOn = occurredOn;
    this.categoryId = categoryId;
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
