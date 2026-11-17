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
   * The balance is computed, never stored: the opening balance plus the account's inflows minus
   * outflows. AccountService works out {@code netFlowPaise} from the live transactions.
   */
  public static AccountResponse from(Account a, long netFlowPaise) {
    return new AccountResponse(
        a.getId(),
        a.getName(),
        a.getType(),
        a.getOpeningBalancePaise(),
        Math.addExact(a.getOpeningBalancePaise(), netFlowPaise),
        a.getArchivedAt());
  }
}
