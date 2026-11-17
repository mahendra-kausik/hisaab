package in.hisaab.accounts.dto;

import in.hisaab.accounts.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code openingBalancePaise} is optional (null means 0) and may be negative. */
public record CreateAccountRequest(
    @NotBlank @Size(max = 100) String name, @NotNull AccountType type, Long openingBalancePaise) {}
