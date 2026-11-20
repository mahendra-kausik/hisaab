package in.hisaab.attachments;

import in.hisaab.attachments.dto.AttachmentResponse;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.dues.DueService;
import in.hisaab.transactions.TransactionService;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Attachments on transactions and dues. The parent is always checked through its own service (which
 * checks workspace access and that it is live), so a soft-deleted or foreign parent is a 404 here
 * too. Files go to object storage; only metadata is kept in Postgres.
 */
@Service
public class AttachmentService {

  private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

  static final long MAX_BYTES = 10L * 1024 * 1024;
  static final Set<String> TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");

  /** An attachment's details plus an open stream of its bytes; the caller closes the stream. */
  public record Download(Attachment attachment, ResponseInputStream<GetObjectResponse> content) {}

  private final AttachmentRepository attachments;
  private final ObjectStorage storage;
  private final TransactionService transactions;
  private final DueService dues;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final Clock clock;

  public AttachmentService(
      AttachmentRepository attachments,
      ObjectStorage storage,
      TransactionService transactions,
      DueService dues,
      WorkspaceAccess access,
      CurrentUser currentUser,
      Clock clock) {
    this.attachments = attachments;
    this.storage = storage;
    this.transactions = transactions;
    this.dues = dues;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AttachmentResponse> listForTransaction(UUID workspaceId, UUID transactionId) {
    transactions.get(workspaceId, transactionId);
    return attachments
        .findByWorkspaceIdAndTransactionIdOrderByCreatedAt(workspaceId, transactionId)
        .stream()
        .map(AttachmentResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AttachmentResponse> listForDue(UUID workspaceId, UUID dueId) {
    dues.get(workspaceId, dueId);
    return attachments.findByWorkspaceIdAndDueIdOrderByCreatedAt(workspaceId, dueId).stream()
        .map(AttachmentResponse::from)
        .toList();
  }

  @Transactional
  public AttachmentResponse uploadToTransaction(
      UUID workspaceId,
      UUID transactionId,
      String fileName,
      String contentType,
      long length,
      InputStream in) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var transaction = transactions.get(workspaceId, transactionId);
    if (transaction.type().isTransfer()) {
      // Money moved between accounts has no receipt of its own; refuse rather than guess a leg.
      throw new ValidationException(
          "transfer_leg",
          "Transfers cannot have attachments",
          Map.of("transfer_group_id", transaction.transferGroupId()));
    }
    return store(workspaceId, transactionId, null, fileName, contentType, length, in);
  }

  @Transactional
  public AttachmentResponse uploadToDue(
      UUID workspaceId,
      UUID dueId,
      String fileName,
      String contentType,
      long length,
      InputStream in) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    dues.get(workspaceId, dueId);
    return store(workspaceId, null, dueId, fileName, contentType, length, in);
  }

  /**
   * The transaction ends when this returns, so no database connection is held while the controller
   * streams the file to the browser.
   */
  @Transactional(readOnly = true)
  public Download download(UUID workspaceId, UUID attachmentId) {
    var attachment = findLive(workspaceId, attachmentId);
    return new Download(attachment, storage.get(attachment.getObjectKey()));
  }

  @Transactional
  public void delete(UUID workspaceId, UUID attachmentId) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var attachment = findLive(workspaceId, attachmentId);
    attachments.delete(attachment);
    String key = attachment.getObjectKey();
    // Remove the file only once the row's deletion is committed. If removal then fails, the file is
    // orphaned (harmless but wasted space); the opposite order could leave a row with no file.
    // ponytail: orphaned objects on a failed delete are only logged; add a sweep job if it matters.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              storage.delete(key);
            } catch (RuntimeException e) {
              log.warn("Could not delete object {}; it is orphaned", key, e);
            }
          }
        });
  }

  private AttachmentResponse store(
      UUID workspaceId,
      UUID transactionId,
      UUID dueId,
      String fileName,
      String contentType,
      long length,
      InputStream in) {
    String type =
        contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if (!TYPES.contains(type)) {
      throw UploadRejectedException.unsupportedType();
    }
    if (length < 0) {
      throw UploadRejectedException.lengthRequired();
    }
    if (length > MAX_BYTES) {
      throw UploadRejectedException.tooLarge();
    }
    if (length == 0) {
      throw new ValidationException(
          "empty_file", "The file is empty", Map.of("fields", Map.of("file", "File is empty")));
    }
    String name = fileName == null ? "" : fileName.trim();
    if (name.isEmpty() || name.length() > 200) {
      throw new ValidationException(
          "invalid_file_name",
          "File name must be 1 to 200 characters",
          Map.of("fields", Map.of("file_name", "Must be 1 to 200 characters")));
    }

    // The object key never contains the user's file name.
    String key = "workspaces/" + workspaceId + "/attachments/" + UUID.randomUUID();
    var saved =
        attachments.saveAndFlush(
            new Attachment(
                workspaceId,
                transactionId,
                dueId,
                key,
                name,
                type,
                length,
                currentUser.require(),
                Instant.now(clock)));
    // If the upload fails this throws, and the transaction rolls the row back.
    storage.put(key, type, length, in);
    return AttachmentResponse.from(saved);
  }

  /** Found in this workspace, with a live parent; anything else looks like "not found". */
  private Attachment findLive(UUID workspaceId, UUID attachmentId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    var attachment =
        attachments
            .findByIdAndWorkspaceId(attachmentId, workspaceId)
            .orElseThrow(() -> new NotFoundException("Attachment not found"));
    if (attachment.getTransactionId() != null) {
      transactions.get(workspaceId, attachment.getTransactionId());
    } else {
      dues.get(workspaceId, attachment.getDueId());
    }
    return attachment;
  }
}
