package in.hisaab.budgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodTest {

  @Test
  void startDayOneIsTheCalendarMonth() {
    var p = Period.containing(LocalDate.of(2026, 11, 19), 1);
    assertThat(p.start()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(p.endExclusive()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(p.lastDay()).isEqualTo(LocalDate.of(2026, 11, 30));
  }

  @Test
  void dayBeforeStartDayBelongsToThePreviousMonthsPeriod() {
    var p = Period.containing(LocalDate.of(2026, 11, 27), 28);
    assertThat(p.start()).isEqualTo(LocalDate.of(2026, 10, 28));
    assertThat(p.endExclusive()).isEqualTo(LocalDate.of(2026, 11, 28));
  }

  @Test
  void startDayItselfStartsANewPeriod() {
    assertThat(Period.containing(LocalDate.of(2026, 11, 28), 28).start())
        .isEqualTo(LocalDate.of(2026, 11, 28));
  }

  @Test
  void crossesTheYearBoundary() {
    var p = Period.containing(LocalDate.of(2027, 1, 3), 5);
    assertThat(p.start()).isEqualTo(LocalDate.of(2026, 12, 5));
    assertThat(p.endExclusive()).isEqualTo(LocalDate.of(2027, 1, 5));
  }

  @Test
  void februaryIsHandledAndPreviousSteps() {
    var p = Period.containing(LocalDate.of(2028, 2, 29), 1);
    assertThat(p.lastDay()).isEqualTo(LocalDate.of(2028, 2, 29));
    assertThat(p.previous().start()).isEqualTo(LocalDate.of(2028, 1, 1));
    assertThat(p.previous().endExclusive()).isEqualTo(p.start());
  }
}
