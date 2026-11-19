package in.hisaab.dues;

import in.hisaab.categories.CategoryKind;
import in.hisaab.categories.CategoryService;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.ListResponse;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.dues.dto.CreateDueRequest;
import in.hisaab.dues.dto.DueResponse;
import in.hisaab.dues.dto.UpdateDueRequest;
import in.hisaab.parties.PartyService;
import in.hisaab.projects.ProjectService;
import in.hisaab.transactions.TransactionService;
import in.hisaab.transactions.TransactionType;
import in.hisaab.transactions.dto.TransactionRequest;
import in.hisaab.users.UserRepository;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DueService {

  private final DueRepository dues;
  private final PartyService parties;
  private final ProjectService projects;
  private final CategoryService categories;
  private final TransactionService transactions;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final CurrentUser currentUser;
  private final Clock clock;

  public DueService(
      DueRepository dues,
      PartyService parties,
      ProjectService projects,
      CategoryService categories,
      TransactionService transactions,
      UserRepository users,
      WorkspaceAccess access,
      CurrentUser currentUser,
      Clock clock) {
    this.dues = dues;
    this.parties = parties;
    this.projects = projects;
    this.categories = categories;
    this.transactions = transactions;
    this.users = users;
    this.access = access;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  // ponytail: no paging; a workspace's dues fit in one response. Add a cursor if lists grow.
  @Transactional(readOnly = true)
  public ListResponse<DueResponse> list(UUID workspaceId, UUID partyId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    var rows =
        partyId == null ? dues.listLive(workspaceId) : dues.listLiveForParty(workspaceId, partyId);
    Map<UUID, Long> paid =
        dues.paidByWorkspace(workspaceId).stream()
            .collect(
                Collectors.toMap(DueRepository.Paid::getDueId, DueRepository.Paid::getPaidPaise));
    var today = today();
    return ListResponse.of(
        rows.stream()
            .map(d -> DueResponse.of(d, paid.getOrDefault(d.getId(), 0L), today))
            .toList());
  }

  @Transactional(readOnly = true)
  public DueResponse get(UUID workspaceId, UUID id) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return respond(findLive(workspaceId, id));
  }

  @Transactional
  public DueResponse create(UUID workspaceId, CreateDueRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    parties.requireOpen(workspaceId, request.partyId());
    if (request.projectId() != null) {
      projects.requireOpen(workspaceId, request.projectId());
    }
    requireCategory(workspaceId, request.direction(), request.categoryId());

    // The money handed over: lending is an expense, borrowing is income. It is not a payment (no
    // due_id), so it does not reduce what is outstanding. Same database transaction as the due.
    UUID originId = null;
    if (request.origin() != null) {
      var origin = request.origin();
      var type =
          request.direction() == DueDirection.RECEIVABLE
              ? TransactionType.EXPENSE
              : TransactionType.INCOME;
      originId =
          transactions
              .create(
                  workspaceId,
                  new TransactionRequest(
                      origin.accountId(),
                      type,
                      request.amountPaise(),
                      origin.occurredOn(),
                      origin.categoryId(),
                      request.partyId(),
                      request.projectId(),
                      null,
                      origin.paymentMethod(),
                      origin.note(),
                      null))
              .id();
    }

    var due =
        new Due(
            workspaceId,
            currentUser.require(),
            request.partyId(),
            request.direction(),
            request.amountPaise(),
            request.reason().trim(),
            request.dueDate(),
            request.projectId(),
            request.categoryId(),
            originId,
            Instant.now(clock));
    return respond(dues.saveAndFlush(due));
  }

  /** PUT: replaces every editable field; anything left out of the request is cleared. */
  @Transactional
  public DueResponse replace(UUID workspaceId, UUID id, UpdateDueRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var due = lockLive(workspaceId, id);
    // An archived project can stay linked, but not be newly linked.
    if (request.projectId() != null && !request.projectId().equals(due.getProjectId())) {
      projects.requireOpen(workspaceId, request.projectId());
    }
    requireCategory(workspaceId, due.getDirection(), request.categoryId());
    if (request.amountPaise() < dues.paidFor(id)) {
      throw new ValidationException(
          "amount_below_paid",
          "The amount cannot be less than what has already been paid",
          Map.of("fields", Map.of("amount_paise", "Less than the amount already paid")));
    }
    due.replace(
        request.amountPaise(),
        request.reason().trim(),
        request.dueDate(),
        request.projectId(),
        request.categoryId(),
        Instant.now(clock));
    return respond(dues.saveAndFlush(due));
  }

  /** A cancelled due refuses new payments; the ones already made stay. Cancelling twice is fine. */
  @Transactional
  public DueResponse cancel(UUID workspaceId, UUID id) {
    return setCancelled(workspaceId, id, true);
  }

  @Transactional
  public DueResponse reopen(UUID workspaceId, UUID id) {
    return setCancelled(workspaceId, id, false);
  }

  private DueResponse setCancelled(UUID workspaceId, UUID id, boolean cancelled) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var due = lockLive(workspaceId, id);
    due.setCancelled(cancelled, Instant.now(clock));
    return respond(dues.saveAndFlush(due));
  }

  // Receivables are paid back as income, payables paid off as expense, so the category kind
  // follows the direction.
  private void requireCategory(UUID workspaceId, DueDirection direction, UUID categoryId) {
    if (categoryId != null) {
      var kind = direction == DueDirection.RECEIVABLE ? CategoryKind.INCOME : CategoryKind.EXPENSE;
      categories.requireForType(workspaceId, categoryId, kind);
    }
  }

  private DueResponse respond(Due due) {
    return DueResponse.of(due, dues.paidFor(due.getId()), today());
  }

  private Due findLive(UUID workspaceId, UUID id) {
    // By id AND workspace: an id from another workspace is simply "not found".
    return dues.findLive(id, workspaceId).orElseThrow(DueService::notFound);
  }

  private Due lockLive(UUID workspaceId, UUID id) {
    return dues.lockLive(id, workspaceId).orElseThrow(DueService::notFound);
  }

  private LocalDate today() {
    var user = users.findById(currentUser.require()).orElseThrow();
    return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Due not found");
  }
}
