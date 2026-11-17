package in.hisaab.accounts.dto;

import in.hisaab.accounts.AccountType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH body: any field left out (null) stays unchanged. */
public record UpdateAccountRequest(
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
    AccountType type,
    Long openingBalancePaise,
    Boolean archived) {}
