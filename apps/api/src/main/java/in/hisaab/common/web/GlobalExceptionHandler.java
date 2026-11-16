package in.hisaab.common.web;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * {@code @RestControllerAdvice} makes this class a global catch for exceptions thrown by any
 * controller. It extends Spring's {@code ResponseEntityExceptionHandler}, which already knows
 * Spring MVC's own exceptions (404 for unknown routes, 405, 415, ...). We override one method so
 * all of them come out in our error shape, then add handlers for our domain exceptions and a last
 * catch-all that returns a generic 500 and never leaks internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("internal_error", "Something went wrong", null));
  }

  /** Bean Validation failed on a request body ({@code @Valid}). */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, Object> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> fields.putIfAbsent(toSnakeCase(e.getField()), e.getDefaultMessage()));
    return body(status, "validation_failed", "Request validation failed", Map.of("fields", fields));
  }

  /** The request body is not valid JSON, or has the wrong types. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return body(status, "malformed_json", "Request body is not valid JSON", null);
  }

  /** Every other Spring MVC exception: use the HTTP status name, e.g. 404 becomes "not_found". */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object responseBody,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    HttpStatus resolved = HttpStatus.resolve(statusCode.value());
    String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
    String code = reason.toLowerCase(Locale.ROOT).replace(' ', '_');
    return body(statusCode, code, reason, null);
  }

  /**
   * Validation reports Java field names ({@code periodStartDay}), but clients know the JSON names
   * ({@code period_start_day}); Jackson's SNAKE_CASE setting does not apply to error keys.
   */
  private static String toSnakeCase(String javaName) {
    return javaName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  private static ResponseEntity<Object> body(
      HttpStatusCode status, String code, String message, Map<String, Object> details) {
    return ResponseEntity.status(status).body(ErrorResponse.of(code, message, details));
  }
}
