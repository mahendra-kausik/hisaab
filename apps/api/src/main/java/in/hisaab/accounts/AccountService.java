package in.hisaab.accounts;

import in.hisaab.accounts.AccountRepository.NetFlow;
import in.hisaab.accounts.dto.AccountResponse;
import in.hisaab.accounts.dto.CreateAccountRequest;
import in.hisaab.accounts.dto.UpdateAccountRequest;
import in.hisaab.common.web.ConflictException;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private final AccountRepository accounts;
  private final WorkspaceAccess access;
  private final Clock clock;

  public AccountService(AccountRepository accounts, WorkspaceAccess access, Clock clock) {
    this.accounts = accounts;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> list(UUID workspaceId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    // One grouped query for all balances, rather than one query per account.
    Map<UUID, Long> netFlows =
        accounts.netFlowsByWorkspace(workspaceId).stream()
            .collect(Collectors.toMap(NetFlow::getAccountId, NetFlow::getNetPaise));
    return accounts.findByWorkspaceIdOrderByName(workspaceId).stream()
        .map(a -> AccountResponse.from(a, netFlows.getOrDefault(a.getId(), 0L)))
        .toList();
  }

  /**
   * For transactions: the account must belong to this workspace and not be archived. Not being in
   * the workspace looks the same as not existing. Called after the caller's own access check.
   */
  @Transactional(readOnly = true)
  public void requireOpen(UUID workspaceId, UUID accountId) {
    var account =
        accounts
            .findByIdAndWorkspaceId(accountId, workspaceId)
            .orElseThrow(() ->
                new ValidationException(
                    "invalid_account",
                    "Account not found in this workspace",
                    Map.of("fields", Map.of("account_id", "Not an account in this workspace"))));
    if (account.getArchivedAt() != null) {
      throw new ValidationException(
          "account_archived",
          "This account is archived",
          Map.of("fields", Map.of("account_id", "Account is archived")));
    }
  }

  @Transactional
  public AccountResponse create(UUID workspaceId, CreateAccountRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    long opening = request.openingBalancePaise() != null ? request.openingBalancePaise() : 0;
    var account =
        new Account(
            workspaceId, request.name().trim(), request.type(), opening, Instant.now(clock));
    return toResponse(saveUnique(account));
  }

  @Transactional
  public AccountResponse update(UUID workspaceId, UUID accountId, UpdateAccountRequest patch) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    // Looking up by id AND workspace: an id from another workspace is simply "not found".
    var account =
        accounts
            .findByIdAndWorkspaceId(accountId, workspaceId)
            .orElseThrow(() -> new NotFoundException("Account not found"));
    var now = Instant.now(clock);
    // Null means "not in the request": keep the current value.
    account.update(
        patch.name() != null ? patch.name().trim() : account.getName(),
        patch.type() != null ? patch.type() : account.getType(),
        patch.openingBalancePaise() != null
            ? patch.openingBalancePaise()
            : account.getOpeningBalancePaise(),
        now);
    if (patch.archived() != null) {
      account.setArchived(patch.archived(), now);
    }
    return toResponse(saveUnique(account));
  }

  /** The Cash account every new workspace starts with. Called by WorkspaceService.create. */
  @Transactional
  public void createDefaults(UUID workspaceId) {
    accounts.save(new Account(workspaceId, "Cash", AccountType.CASH, 0, Instant.now(clock)));
  }

  private AccountResponse toResponse(Account account) {
    return AccountResponse.from(account, accounts.netFlowForAccount(account.getId()));
  }

  // saveAndFlush sends the SQL now. A plain save would wait until commit, so the unique-name
  // violation would surface after this method returned and become a 500 instead of a 409.
  private Account saveUnique(Account account) {
    try {
      return accounts.saveAndFlush(account);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("name_taken", "An account with this name already exists");
    }
  }
}
