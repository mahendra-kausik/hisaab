package in.hisaab.exports;

import in.hisaab.categories.CategoryService;
import in.hisaab.categories.dto.CategoryResponse;
import in.hisaab.dues.DueDirection;
import in.hisaab.dues.DueService;
import in.hisaab.dues.DueState;
import in.hisaab.dues.dto.DueResponse;
import in.hisaab.parties.PartyService;
import in.hisaab.transactions.TransactionService;
import in.hisaab.transactions.TransactionType;
import in.hisaab.transactions.dto.TransactionFilter;
import in.hisaab.transactions.dto.TransactionResponse;
import in.hisaab.users.UserService;
import in.hisaab.workspaces.WorkspaceMembership;
import in.hisaab.workspaces.WorkspaceService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers everything one party has (dues and transactions, all time) through the other features'
 * services, so their access checks and RLS apply, and hands it to {@link StatementPdf}. The totals
 * follow the party ledger page: paid is expenses, received is income, and the net balance is what
 * they still owe minus what you still owe, ignoring cancelled dues.
 */
@Service
public class PartyStatementService {

  public record StatementFile(String fileName, byte[] bytes) {}

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final PartyService parties;
  private final DueService dues;
  private final TransactionService transactions;
  private final CategoryService categories;
  private final WorkspaceService workspaces;
  private final UserService users;
  private final Clock clock;

  public PartyStatementService(
      PartyService parties,
      DueService dues,
      TransactionService transactions,
      CategoryService categories,
      WorkspaceService workspaces,
      UserService users,
      Clock clock) {
    this.parties = parties;
    this.dues = dues;
    this.transactions = transactions;
    this.categories = categories;
    this.workspaces = workspaces;
    this.users = users;
    this.clock = clock;
  }

  // ponytail: built in memory from every row of the party; page through a job if one party ever
  // has thousands of rows.
  @Transactional(readOnly = true)
  public StatementFile statement(UUID workspaceId, UUID partyId) {
    var party = parties.get(workspaceId, partyId); // also the access check and the 404
    var today = LocalDate.now(clock.withZone(ZoneId.of(users.me().timezone())));

    var workspaceName =
        workspaces.listForCurrentUser().stream()
            .filter(w -> w.id().equals(workspaceId))
            .map(WorkspaceMembership::name)
            .findFirst()
            .orElse("");
    Map<UUID, String> categoryNames =
        categories.list(workspaceId).stream()
            .collect(Collectors.toMap(CategoryResponse::id, CategoryResponse::name));

    // Open dues first, cancelled last; the service's order (due date) holds inside each group.
    var dueList =
        dues.list(workspaceId, partyId).items().stream()
            .sorted(Comparator.comparing(d -> d.state() == DueState.CANCELLED))
            .toList();
    long net = 0;
    var dueRows = new ArrayList<List<String>>();
    for (DueResponse d : dueList) {
      if (d.state() != DueState.CANCELLED) {
        net +=
            d.direction() == DueDirection.RECEIVABLE ? d.outstandingPaise() : -d.outstandingPaise();
      }
      dueRows.add(
          List.of(
              d.reason(),
              d.direction() == DueDirection.RECEIVABLE ? "They owe you" : "You owe them",
              d.dueDate() == null ? "—" : d.dueDate().format(DAY),
              money(d.amountPaise()),
              money(d.paidPaise()),
              money(d.outstandingPaise()),
              stateLabel(d.state())));
    }

    long paid = 0;
    long received = 0;
    var txRows = new ArrayList<List<String>>();
    for (TransactionResponse t : allTransactions(workspaceId, partyId)) {
      boolean expense = t.type() == TransactionType.EXPENSE;
      if (expense) {
        paid += t.amountPaise();
      } else {
        received += t.amountPaise();
      }
      txRows.add(
          List.of(
              t.occurredOn().format(DAY),
              details(t, categoryNames),
              expense ? money(t.amountPaise()) : "",
              expense ? "" : money(t.amountPaise())));
    }

    var partyLine =
        party.phone() == null
            ? party.role().name().toLowerCase()
            : party.role().name().toLowerCase() + " · " + party.phone();
    var pdf =
        StatementPdf.render(
            new StatementPdf.Statement(
                workspaceName,
                party.name(),
                partyLine,
                today.format(DAY),
                money(paid),
                money(received),
                money(Math.abs(net)),
                net > 0 ? "They owe you" : net < 0 ? "You owe them" : "Settled up",
                dueRows,
                txRows));
    return new StatementFile("statement-" + slug(party.name()) + "-" + today + ".pdf", pdf);
  }

  /** Expenses and income with this party (payments included), oldest first. */
  private List<TransactionResponse> allTransactions(UUID workspaceId, UUID partyId) {
    var filter =
        new TransactionFilter(null, null, null, null, null, partyId, null, null, null, null);
    var all = new ArrayList<TransactionResponse>();
    String cursor = null;
    do {
      var page = transactions.list(workspaceId, filter, cursor, 200);
      page.items().stream().filter(t -> !t.type().isTransfer()).forEach(all::add);
      cursor = page.nextCursor();
    } while (cursor != null);
    all.sort(
        Comparator.comparing(TransactionResponse::occurredOn)
            .thenComparing(TransactionResponse::createdAt));
    return all;
  }

  private static String details(TransactionResponse t, Map<UUID, String> categoryNames) {
    if (t.note() != null && !t.note().isBlank()) {
      return t.note().trim();
    }
    return t.categoryId() == null ? "—" : categoryNames.getOrDefault(t.categoryId(), "—");
  }

  /**
   * Paise as "₹1,00,000.00" (Indian grouping: the last three digits, then pairs). Done by hand on
   * the long because Java's NumberFormat can only group by one size, and a double would not be
   * exact.
   */
  static String money(long paise) {
    long abs = Math.abs(paise);
    var rupees = Long.toString(abs / 100);
    var grouped = new StringBuilder();
    int head = rupees.length() - 3;
    for (int i = 0; i < head; i++) {
      if (i > 0 && (head - i) % 2 == 0) {
        grouped.append(',');
      }
      grouped.append(rupees.charAt(i));
    }
    if (head > 0) {
      grouped.append(',');
    }
    grouped.append(rupees.substring(Math.max(head, 0)));
    return "%s₹%s.%02d".formatted(paise < 0 ? "-" : "", grouped, abs % 100);
  }

  static String stateLabel(DueState state) {
    return switch (state) {
      case OPEN -> "Open";
      case PARTIALLY_PAID -> "Partially paid";
      case SETTLED -> "Settled";
      case OVERDUE -> "Overdue";
      case CANCELLED -> "Cancelled";
    };
  }

  /** ASCII only, so the Content-Disposition file name needs no encoding. */
  static String slug(String name) {
    var s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    return s.isEmpty() ? "party" : s;
  }
}
