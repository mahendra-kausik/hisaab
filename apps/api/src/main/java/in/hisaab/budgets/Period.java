package in.hisaab.budgets;

import java.time.LocalDate;

/**
 * One budget/report period: {@code start} inclusive to {@code endExclusive}. Periods run from the
 * user's period start day (1-28, so every month has it) to the day before the next one.
 */
public record Period(LocalDate start, LocalDate endExclusive) {

  public static Period containing(LocalDate day, int startDay) {
    var start =
        day.getDayOfMonth() >= startDay
            ? day.withDayOfMonth(startDay)
            : day.minusMonths(1).withDayOfMonth(startDay);
    return new Period(start, start.plusMonths(1));
  }

  public Period previous() {
    return new Period(start.minusMonths(1), start);
  }

  public LocalDate lastDay() {
    return endExclusive.minusDays(1);
  }
}
