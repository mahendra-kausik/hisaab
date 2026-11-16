package in.hisaab.common.web;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** A business rule rejected the input, beyond what Bean Validation can check (422). */
public class ValidationException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ValidationException(String code, String message, Map<String, Object> details) {
    super(HttpStatus.UNPROCESSABLE_CONTENT, code, message, details);
  }
}
