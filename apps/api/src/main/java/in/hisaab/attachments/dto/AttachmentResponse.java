package in.hisaab.attachments.dto;

import in.hisaab.attachments.Attachment;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** What the API says about an attachment. The object key stays private to the server. */
public record AttachmentResponse(
    @NotNull UUID id,
    @NotNull String fileName,
    @NotNull String mimeType,
    @NotNull long sizeBytes,
    @NotNull Instant createdAt) {

  public static AttachmentResponse from(Attachment a) {
    return new AttachmentResponse(
        a.getId(), a.getFileName(), a.getMimeType(), a.getSizeBytes(), a.getCreatedAt());
  }
}
