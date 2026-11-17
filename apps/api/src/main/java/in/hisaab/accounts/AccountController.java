package in.hisaab.accounts;

import in.hisaab.accounts.dto.AccountResponse;
import in.hisaab.accounts.dto.CreateAccountRequest;
import in.hisaab.accounts.dto.UpdateAccountRequest;
import in.hisaab.common.web.ListResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping
  public ListResponse<AccountResponse> list(@PathVariable UUID workspaceId) {
    return ListResponse.of(accountService.list(workspaceId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody CreateAccountRequest request) {
    return accountService.create(workspaceId, request);
  }

  @PatchMapping("/{accountId}")
  public AccountResponse update(
      @PathVariable UUID workspaceId,
      @PathVariable UUID accountId,
      @Valid @RequestBody UpdateAccountRequest request) {
    return accountService.update(workspaceId, accountId, request);
  }
}
