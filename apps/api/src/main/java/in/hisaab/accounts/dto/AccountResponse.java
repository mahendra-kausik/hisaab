package in.hisaab.accounts.dto;

import in.hisaab.accounts.Account;
import in.hisaab.accounts.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String name,
    AccountType type,
    long openingBalancePaise,
    long balancePaise,
    Instant archivedAt) {

  /**
   * The balance is computed, never stored. Today it equals the opening balance because there are no
   * transactions yet; U4 adds the account's inflows minus outflows here.
   */
  public static AccountResponse from(Account a) {
    return new AccountResponse(
        a.getId(),
        a.getName(),
        a.getType(),
        a.getOpeningBalancePaise(),
        a.getOpeningBalancePaise(),
        a.getArchivedAt());
  }
}
