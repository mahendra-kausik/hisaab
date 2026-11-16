package in.hisaab.common.web;

import org.springframework.http.HttpStatus;

/** The thing asked for does not exist, or the caller may not know it exists (404). */
public class NotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "not_found", message, null);
  }
}
