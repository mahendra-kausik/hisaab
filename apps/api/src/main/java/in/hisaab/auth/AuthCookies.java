package in.hisaab.auth;

import static in.hisaab.common.security.CookieBearerTokenResolver.ACCESS_COOKIE;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Builds the two auth cookies. Both are {@code HttpOnly} (JavaScript cannot read them, so an XSS
 * bug cannot steal them), {@code Secure} (HTTPS only; browsers also allow it on localhost) and
 * {@code SameSite=Lax} (not sent on cross-site POSTs).
 *
 * <p>The refresh cookie's path is narrowed to the auth endpoints, so it is never sent with ordinary
 * API calls.
 */
final class AuthCookies {

  static final String REFRESH_COOKIE = "hisaab_refresh";

  private static final String ACCESS_PATH = "/api";
  private static final String REFRESH_PATH = "/api/v1/auth";

  private AuthCookies() {}

  static ResponseCookie access(String token) {
    return build(ACCESS_COOKIE, token, ACCESS_PATH, TokenService.ACCESS_TTL);
  }

  static ResponseCookie refresh(String token) {
    return build(REFRESH_COOKIE, token, REFRESH_PATH, TokenService.REFRESH_TTL);
  }

  /** Same name and path with an empty value and zero lifetime: the browser deletes the cookie. */
  static ResponseCookie clearedAccess() {
    return build(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO);
  }

  static ResponseCookie clearedRefresh() {
    return build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO);
  }

  private static ResponseCookie build(String name, String value, String path, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path(path)
        .maxAge(maxAge)
        .build();
  }
}
