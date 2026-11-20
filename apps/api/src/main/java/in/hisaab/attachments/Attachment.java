package in.hisaab.attachments;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code attachments}: metadata for a file kept in object storage. Exactly one of
 * transactionId and dueId is set (the table checks this).
 */
@Entity
@Table(name = "attachments")
public class Attachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private UUID transactionId;
  private UUID dueId;
  private String objectKey;
  private String fileName;
  private String mimeType;
  private long sizeBytes;
  private UUID createdBy;
  private Instant createdAt;

  protected Attachment() {}

  public Attachment(
      UUID workspaceId,
      UUID transactionId,
      UUID dueId,
      String objectKey,
      String fileName,
      String mimeType,
      long sizeBytes,
      UUID createdBy,
      Instant now) {
    this.workspaceId = workspaceId;
    this.transactionId = transactionId;
    this.dueId = dueId;
    this.objectKey = objectKey;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.sizeBytes = sizeBytes;
    this.createdBy = createdBy;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public UUID getDueId() {
    return dueId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
