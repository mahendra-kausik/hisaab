package in.hisaab.transactions;

import in.hisaab.accounts.AccountService;
import in.hisaab.accounts.dto.AccountResponse;
import in.hisaab.categories.CategoryService;
import in.hisaab.categories.dto.CategoryResponse;
import in.hisaab.parties.PartyService;
import in.hisaab.parties.dto.PartyResponse;
import in.hisaab.projects.ProjectService;
import in.hisaab.projects.dto.ProjectResponse;
import in.hisaab.transactions.dto.TransactionFilter;
import in.hisaab.transactions.dto.TransactionResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Builds the CSV for the transactions export. Paging, access checks and RLS come from list(). */
@Service
public class TransactionExportService {

  private static final String HEADER =
      "date,type,amount,account,category,party,project,payment_method,note,tags\r\n";

  private final TransactionService transactions;
  private final AccountService accounts;
  private final CategoryService categories;
  private final PartyService parties;
  private final ProjectService projects;

  public TransactionExportService(
      TransactionService transactions,
      AccountService accounts,
      CategoryService categories,
      PartyService parties,
      ProjectService projects) {
    this.transactions = transactions;
    this.accounts = accounts;
    this.categories = categories;
    this.parties = parties;
    this.projects = projects;
  }

  // ponytail: whole CSV is built in memory; stream it if workspaces reach ~100k rows.
  public String csv(UUID workspaceId, TransactionFilter filter) {
    Map<UUID, String> accountNames =
        accounts.list(workspaceId).stream()
            .collect(Collectors.toMap(AccountResponse::id, AccountResponse::name));
    Map<UUID, String> categoryNames =
        categories.list(workspaceId).stream()
            .collect(Collectors.toMap(CategoryResponse::id, CategoryResponse::name));
    Map<UUID, String> partyNames =
        parties.list(workspaceId).stream()
            .collect(Collectors.toMap(PartyResponse::id, PartyResponse::name));
    Map<UUID, String> projectNames =
        projects.list(workspaceId).stream()
            .collect(Collectors.toMap(ProjectResponse::id, ProjectResponse::name));

    var out = new StringBuilder(HEADER);
    String cursor = null;
    do {
      var page = transactions.list(workspaceId, filter, cursor, 200);
      for (TransactionResponse t : page.items()) {
        row(
            out,
            t.occurredOn().toString(),
            t.type().json(),
            BigDecimal.valueOf(t.amountPaise(), 2).toPlainString(),
            accountNames.getOrDefault(t.accountId(), ""),
            t.categoryId() == null ? "" : categoryNames.getOrDefault(t.categoryId(), ""),
            t.partyId() == null ? "" : partyNames.getOrDefault(t.partyId(), ""),
            t.projectId() == null ? "" : projectNames.getOrDefault(t.projectId(), ""),
            t.paymentMethod() == null ? "" : t.paymentMethod().json(),
            t.note() == null ? "" : t.note(),
            String.join(";", t.tags()));
      }
      cursor = page.nextCursor();
    } while (cursor != null);
    return out.toString();
  }

  private static void row(StringBuilder out, String... cells) {
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(escape(cells[i]));
    }
    out.append("\r\n");
  }

  /**
   * RFC 4180 quoting, plus a leading apostrophe on cells a spreadsheet would run as a formula. The
   * amount column is exempt: it is always a plain number, and negatives are legitimate there.
   */
  static String escape(String cell) {
    if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0 && !isNumber(cell)) {
      cell = "'" + cell;
    }
    if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
      return '"' + cell.replace("\"", "\"\"") + '"';
    }
    return cell;
  }

  private static boolean isNumber(String cell) {
    try {
      new BigDecimal(cell);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
