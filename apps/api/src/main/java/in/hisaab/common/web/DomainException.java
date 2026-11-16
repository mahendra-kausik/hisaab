package in.hisaab.common.web;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base class for errors services throw on purpose (not found, conflict, ...). {@code
 * GlobalExceptionHandler} turns them into the standard error response.
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String code;
  // Map is not Serializable, so this field is transient (also keeps -Xlint:serial quiet).
  private final transient Map<String, Object> details;

  protected DomainException(
      HttpStatus status, String code, String message, Map<String, Object> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
