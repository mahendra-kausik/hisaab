package in.hisaab.dues;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalDate;

/** Never stored: worked out from the due, what has been paid and today's date. */
public enum DueState {
  OPEN,
  PARTIALLY_PAID,
  SETTLED,
  OVERDUE,
  CANCELLED;

  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  /** Order matters: cancelled beats settled beats overdue beats partially paid beats open. */
  public static DueState of(Due due, long paidPaise, LocalDate today) {
    if (due.getCancelledAt() != null) {
      return CANCELLED;
    }
    if (paidPaise >= due.getAmountPaise()) {
      return SETTLED;
    }
    if (due.getDueDate() != null && due.getDueDate().isBefore(today)) {
      return OVERDUE;
    }
    return paidPaise > 0 ? PARTIALLY_PAID : OPEN;
  }
}
