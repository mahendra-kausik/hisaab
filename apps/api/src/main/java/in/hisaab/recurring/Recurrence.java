package in.hisaab.recurring;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The dates of a schedule: {@code anchor + k * interval} for k = 0, 1, 2... Every date is counted
 * from the anchor, never from the previous date, so a monthly rule anchored on 31 January gives 28
 * (or 29) February, then 31 March again; stepping from the previous date would drift to the 28th
 * for good.
 */
public final class Recurrence {

  private Recurrence() {}

  static LocalDate occurrence(LocalDate anchor, Frequency frequency, int interval, long k) {
    long steps = k * interval;
    return switch (frequency) {
      case WEEKLY -> anchor.plusWeeks(steps);
      case MONTHLY -> anchor.plusMonths(steps);
      case YEARLY -> anchor.plusYears(steps);
    };
  }

  /** The first occurrence strictly after {@code day}. */
  public static LocalDate after(
      LocalDate anchor, Frequency frequency, int interval, LocalDate day) {
    // ponytail: linear scan from the anchor; fine for years of weekly rules, jump ahead if not.
    long k = 0;
    while (!occurrence(anchor, frequency, interval, k).isAfter(day)) {
      k++;
    }
    return occurrence(anchor, frequency, interval, k);
  }

  /**
   * Up to {@code count} occurrences starting at {@code from} (itself an occurrence), none past
   * {@code endsOn}.
   */
  public static List<LocalDate> upcoming(
      LocalDate anchor,
      Frequency frequency,
      int interval,
      LocalDate from,
      LocalDate endsOn,
      int count) {
    var dates = new ArrayList<LocalDate>();
    var date = from;
    while (dates.size() < count && (endsOn == null || !date.isAfter(endsOn))) {
      dates.add(date);
      date = after(anchor, frequency, interval, date);
    }
    return dates;
  }
}
