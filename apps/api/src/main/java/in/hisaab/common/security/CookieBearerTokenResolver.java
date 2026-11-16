package in.hisaab.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Tells Spring Security where to find the access token: the browser sends it as a cookie, a future
 * mobile app sends it as an {@code Authorization: Bearer} header. The cookie wins if both are
 * present.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

  public static final String ACCESS_COOKIE = "hisaab_access";

  private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    // The auth endpoints (login, refresh, ...) are how you get a *new* token. If an expired or
    // garbage cookie were checked there, refresh would fail with 401 for exactly the people
    // who need it, so tokens are ignored on those routes.
    if (request.getRequestURI().startsWith("/api/v1/auth/")) {
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
          return cookie.getValue();
        }
      }
    }
    return headerResolver.resolve(request);
  }
}
