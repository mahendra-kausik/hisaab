package in.hisaab.auth;

import static org.springframework.http.HttpHeaders.SET_COOKIE;

import in.hisaab.auth.TokenService.IssuedTokens;
import in.hisaab.auth.dto.LoginRequest;
import in.hisaab.auth.dto.SignupRequest;
import in.hisaab.users.dto.MeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tokens travel only in httpOnly cookies, never in a response body that JavaScript could read. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/signup")
  public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest request) {
    var result = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .headers(h -> addSessionCookies(h, result.tokens()))
        .body(result.me());
  }

  @PostMapping("/login")
  public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request) {
    var result = authService.login(request);
    return ResponseEntity.ok()
        .headers(h -> addSessionCookies(h, result.tokens()))
        .body(result.me());
  }

  // @CookieValue reads one cookie from the request; required = false lets a missing cookie
  // reach the service, which answers with a proper 401 instead of a generic 400.
  @PostMapping("/refresh")
  public ResponseEntity<Void> refresh(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken) {
    var tokens = authService.refresh(refreshToken);
    return ResponseEntity.noContent().headers(h -> addSessionCookies(h, tokens)).build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(SET_COOKIE, AuthCookies.clearedAccess().toString())
        .header(SET_COOKIE, AuthCookies.clearedRefresh().toString())
        .build();
  }

  // `add` (not `set`) so both cookies get their own Set-Cookie header.
  private static void addSessionCookies(HttpHeaders headers, IssuedTokens tokens) {
    headers.add(SET_COOKIE, AuthCookies.access(tokens.accessToken()).toString());
    headers.add(SET_COOKIE, AuthCookies.refresh(tokens.refreshToken()).toString());
  }
}
