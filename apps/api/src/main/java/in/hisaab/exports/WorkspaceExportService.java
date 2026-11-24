package in.hisaab.exports;

import in.hisaab.accounts.AccountService;
import in.hisaab.accounts.dto.AccountResponse;
import in.hisaab.categories.CategoryService;
import in.hisaab.categories.dto.CategoryResponse;
import in.hisaab.dues.DueDirection;
import in.hisaab.dues.DueService;
import in.hisaab.parties.PartyService;
import in.hisaab.parties.dto.PartyResponse;
import in.hisaab.projects.ProjectService;
import in.hisaab.projects.dto.ProjectResponse;
import in.hisaab.transactions.TransactionService;
import in.hisaab.transactions.dto.TransactionFilter;
import in.hisaab.users.UserService;
import in.hisaab.workspaces.WorkspaceMembership;
import in.hisaab.workspaces.WorkspaceService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the Excel workbook for a whole workspace: every transaction and every due, all time. Like
 * the CSV and PDF exports it reads through the other features' services, so their access checks
 * (VIEWER) and RLS apply. Amounts are number cells so SUM and sorting work; names are looked up
 * once instead of per row.
 */
@Service
public class WorkspaceExportService {

  public record ExportFile(String fileName, byte[] bytes) {}

  // Indian grouping (1,00,00,000.00 / 1,00,000.00 / 1,000.00); Excel picks the section by value.
  private static final String MONEY_FORMAT =
      "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";
  private static final String DATE_FORMAT = "dd-mmm-yyyy";

  private final TransactionService transactions;
  private final DueService dues;
  private final AccountService accounts;
  private final CategoryService categories;
  private final PartyService parties;
  private final ProjectService projects;
  private final WorkspaceService workspaces;
  private final UserService users;
  private final Clock clock;

  public WorkspaceExportService(
      TransactionService transactions,
      DueService dues,
      AccountService accounts,
      CategoryService categories,
      PartyService parties,
      ProjectService projects,
      WorkspaceService workspaces,
      UserService users,
      Clock clock) {
    this.transactions = transactions;
    this.dues = dues;
    this.accounts = accounts;
    this.categories = categories;
    this.parties = parties;
    this.projects = projects;
    this.workspaces = workspaces;
    this.users = users;
    this.clock = clock;
  }

  // ponytail: the whole workbook is built in memory (XSSFWorkbook); switch to SXSSFWorkbook, which
  // streams rows to disk, if a workspace ever reaches ~100k rows.
  @Transactional(readOnly = true)
  public ExportFile export(UUID workspaceId) {
    // Listing dues is also the VIEWER access check, so a non-member fails before anything is built.
    var dueList = dues.list(workspaceId, null).items();
    var today = LocalDate.now(clock.withZone(ZoneId.of(users.me().timezone())));
    var workspaceName =
        workspaces.listForCurrentUser().stream()
            .filter(w -> w.id().equals(workspaceId))
            .map(WorkspaceMembership::name)
            .findFirst()
            .orElse("");

    var accountNames =
        names(accounts.list(workspaceId), AccountResponse::id, AccountResponse::name);
    var categoryNames =
        names(categories.list(workspaceId), CategoryResponse::id, CategoryResponse::name);
    var partyNames = names(parties.list(workspaceId), PartyResponse::id, PartyResponse::name);
    var projectNames =
        names(projects.list(workspaceId), ProjectResponse::id, ProjectResponse::name);

    try (var book = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var header = book.createCellStyle();
      var bold = book.createFont();
      bold.setBold(true);
      header.setFont(bold);
      var money = book.createCellStyle();
      money.setDataFormat(book.createDataFormat().getFormat(MONEY_FORMAT));
      var date = book.createCellStyle();
      date.setDataFormat(book.createDataFormat().getFormat(DATE_FORMAT));

      var txSheet =
          sheet(
              book,
              "Transactions",
              header,
              new String[] {
                "Date",
                "Type",
                "Amount (₹)",
                "Account",
                "Category",
                "Party",
                "Project",
                "Payment method",
                "Note",
                "Tags"
              },
              new int[] {13, 10, 15, 18, 20, 22, 20, 15, 40, 25});
      int r = 1;
      String cursor = null;
      var all = new TransactionFilter(null, null, null, null, null, null, null, null, null, null);
      do {
        var page = transactions.list(workspaceId, all, cursor, 200);
        for (var t : page.items()) {
          Row row = txSheet.createRow(r++);
          dateCell(row, 0, t.occurredOn(), date);
          text(row, 1, t.type().json());
          moneyCell(row, 2, t.amountPaise(), money);
          text(row, 3, accountNames.getOrDefault(t.accountId(), ""));
          text(
              row, 4, t.categoryId() == null ? "" : categoryNames.getOrDefault(t.categoryId(), ""));
          text(row, 5, t.partyId() == null ? "" : partyNames.getOrDefault(t.partyId(), ""));
          text(row, 6, t.projectId() == null ? "" : projectNames.getOrDefault(t.projectId(), ""));
          text(row, 7, t.paymentMethod() == null ? "" : t.paymentMethod().json());
          text(row, 8, t.note() == null ? "" : t.note());
          text(row, 9, String.join(", ", t.tags()));
        }
        cursor = page.nextCursor();
      } while (cursor != null);

      var dueSheet =
          sheet(
              book,
              "Dues",
              header,
              new String[] {
                "Party",
                "Direction",
                "Reason",
                "Due date",
                "Amount (₹)",
                "Paid (₹)",
                "Outstanding (₹)",
                "State",
                "Project",
                "Category"
              },
              new int[] {22, 14, 35, 13, 15, 15, 17, 15, 20, 20});
      r = 1;
      for (var d : dueList) {
        Row row = dueSheet.createRow(r++);
        text(row, 0, partyNames.getOrDefault(d.partyId(), ""));
        text(row, 1, d.direction() == DueDirection.RECEIVABLE ? "They owe you" : "You owe them");
        text(row, 2, d.reason());
        if (d.dueDate() != null) {
          dateCell(row, 3, d.dueDate(), date);
        }
        moneyCell(row, 4, d.amountPaise(), money);
        moneyCell(row, 5, d.paidPaise(), money);
        moneyCell(row, 6, d.outstandingPaise(), money);
        text(row, 7, PartyStatementService.stateLabel(d.state()));
        text(row, 8, d.projectId() == null ? "" : projectNames.getOrDefault(d.projectId(), ""));
        text(row, 9, d.categoryId() == null ? "" : categoryNames.getOrDefault(d.categoryId(), ""));
      }

      book.write(out);
      return new ExportFile(
          "hisaab-" + PartyStatementService.slug(workspaceName) + "-" + today + ".xlsx",
          out.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static <T> Map<UUID, String> names(
      Iterable<T> items, Function<T, UUID> id, Function<T, String> name) {
    var map = new HashMap<UUID, String>();
    items.forEach(i -> map.put(id.apply(i), name.apply(i)));
    return map;
  }

  /**
   * A bold, frozen header row and fixed column widths. autoSizeColumn is avoided on purpose: it
   * needs AWT fonts, which a bare server may not have.
   */
  private static Sheet sheet(
      XSSFWorkbook book, String name, CellStyle header, String[] titles, int[] widths) {
    var sheet = book.createSheet(name);
    Row row = sheet.createRow(0);
    for (int i = 0; i < titles.length; i++) {
      var cell = row.createCell(i);
      cell.setCellValue(titles[i]);
      cell.setCellStyle(header);
      sheet.setColumnWidth(i, widths[i] * 256);
    }
    sheet.createFreezePane(0, 1);
    return sheet;
  }

  // setCellValue(String) always writes a text cell, so a note like "=SUM(A1)" is never run as a
  // formula and needs no guard (unlike the CSV export).
  private static void text(Row row, int col, String value) {
    row.createCell(col).setCellValue(value);
  }

  private static void dateCell(Row row, int col, LocalDate value, CellStyle style) {
    var cell = row.createCell(col);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  /**
   * Paise stay a long everywhere else. A double appears only here, because the xlsx format can
   * store nothing but doubles; it is exact for any amount below about ₹90 trillion.
   */
  private static void moneyCell(Row row, int col, long paise, CellStyle style) {
    var cell = row.createCell(col);
    cell.setCellValue(BigDecimal.valueOf(paise, 2).doubleValue());
    cell.setCellStyle(style);
  }
}
