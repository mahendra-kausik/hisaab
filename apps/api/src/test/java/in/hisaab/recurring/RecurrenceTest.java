package in.hisaab.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrenceTest {

  @Test
  void monthlyOnTheThirtyFirstClampsInShortMonthsWithoutDrifting() {
    var anchor = LocalDate.of(2026, 1, 31);

    assertThat(Recurrence.upcoming(anchor, Frequency.MONTHLY, 1, anchor, null, 4))
        .containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 30));
  }

  @Test
  void leapDayYearlyFallsBackToTheTwentyEighthThenReturns() {
    var anchor = LocalDate.of(2024, 2, 29);

    assertThat(Recurrence.after(anchor, Frequency.YEARLY, 1, anchor))
        .isEqualTo(LocalDate.of(2025, 2, 28));
    assertThat(Recurrence.after(anchor, Frequency.YEARLY, 4, anchor))
        .isEqualTo(LocalDate.of(2028, 2, 29));
  }

  @Test
  void intervalsAndEndDate() {
    var anchor = LocalDate.of(2026, 11, 1);

    assertThat(Recurrence.upcoming(anchor, Frequency.WEEKLY, 2, anchor, null, 3))
        .containsExactly(anchor, anchor.plusDays(14), anchor.plusDays(28));
    assertThat(Recurrence.upcoming(anchor, Frequency.WEEKLY, 1, anchor, anchor.plusDays(8), 10))
        .containsExactly(anchor, anchor.plusDays(7));
  }

  @Test
  void afterIsStrictlyLater() {
    var anchor = LocalDate.of(2026, 11, 1);

    assertThat(Recurrence.after(anchor, Frequency.WEEKLY, 1, anchor.minusDays(1)))
        .isEqualTo(anchor);
    assertThat(Recurrence.after(anchor, Frequency.WEEKLY, 1, anchor.plusDays(7)))
        .isEqualTo(anchor.plusDays(14));
  }
}
