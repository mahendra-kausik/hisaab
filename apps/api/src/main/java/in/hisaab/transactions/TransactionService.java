package in.hisaab.transactions;

import in.hisaab.accounts.AccountService;
import in.hisaab.categories.CategoryKind;
import in.hisaab.categories.CategoryService;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.ListResponse;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.transactions.dto.TransactionFilter;
import in.hisaab.transactions.dto.TransactionRequest;
import in.hisaab.transactions.dto.TransactionResponse;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

  private final TransactionRepository transactions;
  private final TransactionQueries queries;
  private final TagRepository tags;
  private final AccountService accounts;
  private final CategoryService categories;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final Clock clock;

  public TransactionService(
      TransactionRepository transactions,
      TransactionQueries queries,
      TagRepository tags,
      AccountService accounts,
      CategoryService categories,
      WorkspaceAccess access,
      CurrentUser currentUser,
      Clock clock) {
    this.transactions = transactions;
    this.queries = queries;
    this.tags = tags;
    this.accounts = accounts;
    this.categories = categories;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ListResponse<TransactionResponse> list(
      UUID workspaceId, TransactionFilter filter, String cursor, int limit) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    var after = cursor == null ? null : TransactionCursor.decode(cursor);
    // Ask for one row more than the page size: if it comes back, there is a next page.
    List<TransactionResponse> rows = queries.page(workspaceId, filter, after, limit + 1);
    if (rows.size() <= limit) {
      return ListResponse.of(rows);
    }
    List<TransactionResponse> items = rows.subList(0, limit);
    return new ListResponse<>(items, TransactionCursor.after(items.getLast()).encode());
  }

  @Transactional(readOnly = true)
  public TransactionResponse get(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return queries.findById(workspaceId, id).orElseThrow(TransactionService::notFound);
  }

  @Transactional
  public TransactionResponse create(UUID workspaceId, TransactionRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    requireManualType(request.type());
    accounts.requireOpen(workspaceId, request.accountId());
    requireCategory(workspaceId, request);

    var transaction =
        new Transaction(
            workspaceId,
            currentUser.require(),
            request.accountId(),
            request.type(),
            request.amountPaise(),
            request.occurredOn(),
            request.categoryId(),
            request.paymentMethod(),
            blankToNull(request.note()),
            Instant.now(clock));
    // saveAndFlush: the row must exist before the tag links that point at it are inserted.
    transaction = transactions.saveAndFlush(transaction);
    saveTags(workspaceId, transaction.getId(), request.tags());
    return reload(workspaceId, transaction.getId());
  }

  /** PUT: replaces every editable field; anything left out of the request is cleared. */
  @Transactional
  public TransactionResponse replace(UUID workspaceId, UUID id, TransactionRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    requireManualType(request.type());
    var transaction = findLive(workspaceId, id);
    // Keeping a transaction on its archived account is fine; moving one onto it is not.
    if (!transaction.getAccountId().equals(request.accountId())) {
      accounts.requireOpen(workspaceId, request.accountId());
    }
    requireCategory(workspaceId, request);

    transaction.replace(
        request.accountId(),
        request.type(),
        request.amountPaise(),
        request.occurredOn(),
        request.categoryId(),
        request.paymentMethod(),
        blankToNull(request.note()),
        Instant.now(clock));
    transactions.saveAndFlush(transaction);
    saveTags(workspaceId, id, request.tags());
    return reload(workspaceId, id);
  }

  /** Soft delete: the row stays (with deleted_at set) so it can be restored. */
  @Transactional
  public void delete(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    findLive(workspaceId, id).softDelete(Instant.now(clock));
  }

  /** Undo for delete. Restoring a transaction that is not deleted changes nothing. */
  @Transactional
  public TransactionResponse restore(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var transaction =
        transactions
            .findByIdAndWorkspaceId(id, workspaceId)
            .orElseThrow(TransactionService::notFound);
    if (transaction.getDeletedAt() != null) {
      transaction.restore(Instant.now(clock));
      transactions.saveAndFlush(transaction);
    }
    return reload(workspaceId, id);
  }

  @Transactional(readOnly = true)
  public List<String> listTags(UUID workspaceId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return tags.listNames(workspaceId);
  }

  private Transaction findLive(UUID workspaceId, UUID id) {
    // By id AND workspace: an id from another workspace is simply "not found".
    return transactions
        .findByIdAndWorkspaceId(id, workspaceId)
        .filter(t -> t.getDeletedAt() == null)
        .orElseThrow(TransactionService::notFound);
  }

  private TransactionResponse reload(UUID workspaceId, UUID id) {
    return queries
        .findById(workspaceId, id)
        .orElseThrow(() -> new IllegalStateException("Transaction vanished inside its own save"));
  }

  // Transfers are a linked pair of rows that U5 creates together; a single row can't be one.
  private static void requireManualType(TransactionType type) {
    if (type.isTransfer()) {
      throw new ValidationException(
          "invalid_type",
          "Transfers are not created here; use the transfers endpoint",
          Map.of("fields", Map.of("type", "Must be expense or income")));
    }
  }

  private void requireCategory(UUID workspaceId, TransactionRequest request) {
    if (request.categoryId() != null) {
      var kind = CategoryKind.valueOf(request.type().name());
      categories.requireForType(workspaceId, request.categoryId(), kind);
    }
  }

  private void saveTags(UUID workspaceId, UUID transactionId, List<String> names) {
    tags.replaceLinks(workspaceId, transactionId, tags.upsert(workspaceId, distinctTrimmed(names)));
  }

  /** Trims names and drops repeats ignoring case; the first spelling wins ("Rent", not "rent"). */
  private static List<String> distinctTrimmed(List<String> names) {
    if (names == null) {
      return List.of();
    }
    Map<String, String> byLowerCase = new LinkedHashMap<>();
    for (String name : names) {
      String trimmed = name.trim();
      byLowerCase.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }
    return List.copyOf(byLowerCase.values());
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Transaction not found");
  }
}
