package in.hisaab.common.web;

import org.springframework.http.HttpStatus;

/** The caller is known but not allowed to do this (403). */
public class ForbiddenException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "forbidden", message, null);
  }
}
