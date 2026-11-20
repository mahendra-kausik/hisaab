package in.hisaab.attachments;

import in.hisaab.attachments.dto.AttachmentResponse;
import in.hisaab.common.web.ListResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uploads send the file as the raw request body (not multipart): {@code Content-Type} is the file's
 * type, {@code Content-Length} its size and {@code ?file_name=} its name. The body is streamed
 * straight to storage, never held in memory.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class AttachmentController {

  private final AttachmentService attachmentService;

  public AttachmentController(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @GetMapping("/transactions/{transactionId}/attachments")
  public ListResponse<AttachmentResponse> listForTransaction(
      @PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
    return ListResponse.of(attachmentService.listForTransaction(workspaceId, transactionId));
  }

  @PostMapping("/transactions/{transactionId}/attachments")
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentResponse uploadToTransaction(
      @PathVariable UUID workspaceId,
      @PathVariable UUID transactionId,
      @RequestParam("file_name") String fileName,
      HttpServletRequest request)
      throws IOException {
    return attachmentService.uploadToTransaction(
        workspaceId,
        transactionId,
        fileName,
        request.getContentType(),
        request.getContentLengthLong(),
        request.getInputStream());
  }

  @GetMapping("/dues/{dueId}/attachments")
  public ListResponse<AttachmentResponse> listForDue(
      @PathVariable UUID workspaceId, @PathVariable UUID dueId) {
    return ListResponse.of(attachmentService.listForDue(workspaceId, dueId));
  }

  @PostMapping("/dues/{dueId}/attachments")
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentResponse uploadToDue(
      @PathVariable UUID workspaceId,
      @PathVariable UUID dueId,
      @RequestParam("file_name") String fileName,
      HttpServletRequest request)
      throws IOException {
    return attachmentService.uploadToDue(
        workspaceId,
        dueId,
        fileName,
        request.getContentType(),
        request.getContentLengthLong(),
        request.getInputStream());
  }

  /**
   * The file itself. The headers stop a wrongly labelled file from running as a page on our origin:
   * {@code nosniff} forbids guessing a different type, and {@code sandbox} gives it no powers.
   */
  @GetMapping("/attachments/{attachmentId}/content")
  public ResponseEntity<InputStreamResource> content(
      @PathVariable UUID workspaceId, @PathVariable UUID attachmentId) {
    var download = attachmentService.download(workspaceId, attachmentId);
    var attachment = download.attachment();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(attachment.getMimeType()))
        .contentLength(attachment.getSizeBytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox")
        .body(new InputStreamResource(download.content()));
  }

  @DeleteMapping("/attachments/{attachmentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID workspaceId, @PathVariable UUID attachmentId) {
    attachmentService.delete(workspaceId, attachmentId);
  }
}
