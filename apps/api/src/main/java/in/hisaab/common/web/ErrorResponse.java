package in.hisaab.common.web;

import java.util.Map;

/**
 * The one error shape every endpoint returns: {@code {"error": {"code", "message", "details"}}}.
 * Field names become snake_case through the global Jackson setting.
 */
public record ErrorResponse(Error error) {

  public record Error(String code, String message, Map<String, Object> details) {}

  public static ErrorResponse of(String code, String message, Map<String, Object> details) {
    return new ErrorResponse(new Error(code, message, details));
  }
}
