package in.hisaab.transactions;

import in.hisaab.accounts.AccountService;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.transactions.dto.TransactionResponse;
import in.hisaab.transactions.dto.TransferRequest;
import in.hisaab.transactions.dto.TransferResponse;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A transfer is two rows of {@code transactions}: a {@code transfer_out} on the source account and
 * a {@code transfer_in} on the destination, sharing a transfer_group_id. Every method here changes
 * both rows inside one transaction, so a transfer can never be half-created, half-edited or
 * half-deleted (invariant 5). The transfer's public id is the group id.
 */
@Service
public class TransferService {

  /** The two rows of one transfer. */
  private record Legs(Transaction out, Transaction in) {}

  private final TransactionRepository transactions;
  private final TransactionQueries queries;
  private final AccountService accounts;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final Clock clock;

  public TransferService(
      TransactionRepository transactions,
      TransactionQueries queries,
      AccountService accounts,
      WorkspaceAccess access,
      CurrentUser currentUser,
      Clock clock) {
    this.transactions = transactions;
    this.queries = queries;
    this.accounts = accounts;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public TransferResponse get(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return read(workspaceId, id);
  }

  @Transactional
  public TransferResponse create(UUID workspaceId, TransferRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    requireDifferentAccounts(request);
    accounts.requireOpen(workspaceId, request.fromAccountId());
    accounts.requireOpen(workspaceId, request.toAccountId());

    UUID groupId = UUID.randomUUID();
    UUID userId = currentUser.require();
    Instant now = Instant.now(clock);
    String note = blankToNull(request.note());
    transactions.saveAndFlush(
        Transaction.transferLeg(
            workspaceId,
            userId,
            groupId,
            request.fromAccountId(),
            TransactionType.TRANSFER_OUT,
            request.amountPaise(),
            request.occurredOn(),
            request.paymentMethod(),
            note,
            now));
    transactions.saveAndFlush(
        Transaction.transferLeg(
            workspaceId,
            userId,
            groupId,
            request.toAccountId(),
            TransactionType.TRANSFER_IN,
            request.amountPaise(),
            request.occurredOn(),
            request.paymentMethod(),
            note,
            now));
    return read(workspaceId, groupId);
  }

  /** PUT: both legs take the new values; anything left out of the request is cleared. */
  @Transactional
  public TransferResponse replace(UUID workspaceId, UUID id, TransferRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    requireDifferentAccounts(request);
    Legs legs = findLiveLegs(workspaceId, id);
    // Keeping a leg on its archived account is fine; moving one onto it is not (same as U4).
    if (!legs.out().getAccountId().equals(request.fromAccountId())) {
      accounts.requireOpen(workspaceId, request.fromAccountId());
    }
    if (!legs.in().getAccountId().equals(request.toAccountId())) {
      accounts.requireOpen(workspaceId, request.toAccountId());
    }

    Instant now = Instant.now(clock);
    String note = blankToNull(request.note());
    legs.out()
        .replace(
            request.fromAccountId(),
            TransactionType.TRANSFER_OUT,
            request.amountPaise(),
            request.occurredOn(),
            null,
            null,
            null,
            request.paymentMethod(),
            note,
            now);
    legs.in()
        .replace(
            request.toAccountId(),
            TransactionType.TRANSFER_IN,
            request.amountPaise(),
            request.occurredOn(),
            null,
            null,
            null,
            request.paymentMethod(),
            note,
            now);
    transactions.saveAndFlush(legs.out());
    transactions.saveAndFlush(legs.in());
    return read(workspaceId, id);
  }

  /** Soft delete of both legs, so balances on both accounts go back as if it never happened. */
  @Transactional
  public void delete(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    Legs legs = findLiveLegs(workspaceId, id);
    Instant now = Instant.now(clock);
    legs.out().softDelete(now);
    legs.in().softDelete(now);
  }

  /** Undo for delete. Restoring a transfer that is not deleted changes nothing. */
  @Transactional
  public TransferResponse restore(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    Legs legs = findLegs(workspaceId, id);
    Instant now = Instant.now(clock);
    for (Transaction leg : List.of(legs.out(), legs.in())) {
      if (leg.getDeletedAt() != null) {
        leg.restore(now);
        transactions.saveAndFlush(leg);
      }
    }
    return read(workspaceId, id);
  }

  /** Both rows of the group, deleted or not. Anything but one out and one in is "not found". */
  private Legs findLegs(UUID workspaceId, UUID id) {
    // By group id AND workspace: an id from another workspace is simply "not found".
    List<Transaction> rows = transactions.findByTransferGroupIdAndWorkspaceId(id, workspaceId);
    Transaction out = null;
    Transaction in = null;
    for (Transaction row : rows) {
      if (row.getType() == TransactionType.TRANSFER_OUT) {
        out = row;
      } else if (row.getType() == TransactionType.TRANSFER_IN) {
        in = row;
      }
    }
    if (rows.size() != 2 || out == null || in == null) {
      throw notFound();
    }
    return new Legs(out, in);
  }

  private Legs findLiveLegs(UUID workspaceId, UUID id) {
    Legs legs = findLegs(workspaceId, id);
    if (legs.out().getDeletedAt() != null || legs.in().getDeletedAt() != null) {
      throw notFound();
    }
    return legs;
  }

  /** Reads the transfer back through the list query so the response matches what GET returns. */
  private TransferResponse read(UUID workspaceId, UUID id) {
    List<TransactionResponse> legs = queries.findTransferLegs(workspaceId, id);
    TransactionResponse out = null;
    TransactionResponse in = null;
    for (TransactionResponse leg : legs) {
      if (leg.type() == TransactionType.TRANSFER_OUT) {
        out = leg;
      } else if (leg.type() == TransactionType.TRANSFER_IN) {
        in = leg;
      }
    }
    if (legs.size() != 2 || out == null || in == null) {
      throw notFound();
    }
    return TransferResponse.from(out, in);
  }

  private static void requireDifferentAccounts(TransferRequest request) {
    if (request.fromAccountId().equals(request.toAccountId())) {
      throw new ValidationException(
          "same_account",
          "A transfer needs two different accounts",
          Map.of("fields", Map.of("to_account_id", "Must differ from from_account_id")));
    }
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Transfer not found");
  }
}
