package in.hisaab.common.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security filters run before Spring MVC, so {@code GlobalExceptionHandler} never sees their
 * errors. This writes the same {@code {"error": {...}}} shape by hand. Messages are fixed strings
 * without quotes, so no JSON escaping is needed.
 */
final class SecurityErrors {

  private SecurityErrors() {}

  static void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"error\":{\"code\":\"%s\",\"message\":\"%s\",\"details\":null}}"
                .formatted(code, message));
  }
}
