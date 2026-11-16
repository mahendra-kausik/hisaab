package in.hisaab.common.web;

import org.springframework.http.HttpStatus;

/** The caller could not be authenticated: bad credentials or an invalid refresh token (401). */
public class UnauthorizedException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UnauthorizedException(String code, String message) {
    super(HttpStatus.UNAUTHORIZED, code, message, null);
  }
}
