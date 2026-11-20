package in.hisaab.attachments;

import in.hisaab.common.web.DomainException;
import org.springframework.http.HttpStatus;

/**
 * An upload was refused before anything was stored: wrong type (415), no length (411), too big
 * (413).
 */
public class UploadRejectedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private UploadRejectedException(HttpStatus status, String code, String message) {
    super(status, code, message, null);
  }

  static UploadRejectedException unsupportedType() {
    return new UploadRejectedException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "unsupported_type",
        "Attach a JPEG, PNG, WebP, HEIC or PDF file");
  }

  static UploadRejectedException lengthRequired() {
    return new UploadRejectedException(
        HttpStatus.LENGTH_REQUIRED, "length_required", "Content-Length header is required");
  }

  static UploadRejectedException tooLarge() {
    return new UploadRejectedException(
        HttpStatus.CONTENT_TOO_LARGE, "file_too_large", "Files can be at most 10 MB");
  }
}
