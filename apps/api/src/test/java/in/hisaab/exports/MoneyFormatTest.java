package in.hisaab.exports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoneyFormatTest {

  @Test
  void groupsTheIndianWay() {
    assertThat(PartyStatementService.money(0)).isEqualTo("₹0.00");
    assertThat(PartyStatementService.money(5)).isEqualTo("₹0.05");
    assertThat(PartyStatementService.money(50_000)).isEqualTo("₹500.00");
    assertThat(PartyStatementService.money(123_456)).isEqualTo("₹1,234.56");
    assertThat(PartyStatementService.money(1_234_500)).isEqualTo("₹12,345.00");
    assertThat(PartyStatementService.money(10_000_000)).isEqualTo("₹1,00,000.00");
    assertThat(PartyStatementService.money(1_234_567_890)).isEqualTo("₹1,23,45,678.90");
    assertThat(PartyStatementService.money(-10_000_000)).isEqualTo("-₹1,00,000.00");
  }
}
