package in.hisaab.common.web;

import org.springframework.http.HttpStatus;

/** The request clashes with current state, e.g. a duplicate or an overpayment (409). */
public class ConflictException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String code, String message) {
    super(HttpStatus.CONFLICT, code, message, null);
  }
}
