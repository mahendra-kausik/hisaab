package in.hisaab.accounts.dto;

import in.hisaab.accounts.Account;
import in.hisaab.accounts.AccountType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    @NotNull UUID id,
    @NotNull String name,
    @NotNull AccountType type,
    @NotNull long openingBalancePaise,
    @NotNull long balancePaise,
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
