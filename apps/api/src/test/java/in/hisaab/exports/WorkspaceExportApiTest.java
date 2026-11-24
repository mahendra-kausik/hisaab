package in.hisaab.exports;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class WorkspaceExportApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void workbookHasTransactionsAndDuesAsRealCells() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String party = create(s, ws, "/parties", "{\"name\": \"Ramesh\", \"role\": \"buyer\"}");
    create(
        s,
        ws,
        "/dues",
        "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": 10000000,"
                .formatted(party)
            + " \"reason\": \"Plot advance\", \"due_date\": \"2026-12-31\"}");
    tx(s, ws, party, 12345, "2026-03-10", "Lunch");
    tx(s, ws, null, 500, "2026-03-11", "=SUM(A1)");

    var result =
        mockMvc
            .perform(get(url(ws)).cookie(auth(s)))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn()
            .getResponse();
    assertThat(result.getHeader("Content-Disposition"))
        .startsWith("attachment; filename=\"hisaab-")
        .endsWith(".xlsx\"");

    try (var book = open(result.getContentAsByteArray())) {
      var txs = book.getSheet("Transactions");
      assertThat(txs.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Amount (₹)");
      assertThat(txs.getLastRowNum()).isEqualTo(2); // header + 2 rows

      // The list is newest first, so the 11 March row (the formula-looking note) comes first.
      var formula = txs.getRow(1);
      assertThat(formula.getCell(8).getCellType()).isEqualTo(CellType.STRING);
      assertThat(formula.getCell(8).getStringCellValue()).isEqualTo("=SUM(A1)");

      var lunch = txs.getRow(2);
      assertThat(lunch.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(lunch.getCell(2).getNumericCellValue()).isEqualTo(123.45);
      assertThat(DateUtil.isCellDateFormatted(lunch.getCell(0))).isTrue();
      assertThat(lunch.getCell(0).getLocalDateTimeCellValue().toLocalDate())
          .isEqualTo(LocalDate.of(2026, 3, 10));
      assertThat(lunch.getCell(1).getStringCellValue()).isEqualTo("expense");
      assertThat(lunch.getCell(3).getStringCellValue()).isEqualTo("Cash");
      assertThat(lunch.getCell(5).getStringCellValue()).isEqualTo("Ramesh");

      var dues = book.getSheet("Dues");
      assertThat(dues.getLastRowNum()).isEqualTo(1);
      var due = dues.getRow(1);
      assertThat(due.getCell(0).getStringCellValue()).isEqualTo("Ramesh");
      assertThat(due.getCell(1).getStringCellValue()).isEqualTo("They owe you");
      assertThat(due.getCell(2).getStringCellValue()).isEqualTo("Plot advance");
      assertThat(due.getCell(3).getLocalDateTimeCellValue().toLocalDate())
          .isEqualTo(LocalDate.of(2026, 12, 31));
      assertThat(due.getCell(4).getNumericCellValue()).isEqualTo(100000.0);
      assertThat(due.getCell(5).getNumericCellValue()).isEqualTo(0.0);
      assertThat(due.getCell(6).getNumericCellValue()).isEqualTo(100000.0);
      assertThat(due.getCell(7).getStringCellValue()).isNotEmpty();
    }
  }

  @Test
  void otherWorkspacesRowsAreNeverIncludedAndNonMembersAreRefused() throws Exception {
    Session mine = fixtures.signup();
    UUID myWs = fixtures.workspaceIdOf(mine);
    tx(mine, myWs, null, 100, "2026-03-10", "mine");

    Session other = fixtures.signup();
    UUID otherWs = fixtures.workspaceIdOf(other);
    tx(other, otherWs, null, 200, "2026-03-10", "theirs");

    try (var book =
        open(
            mockMvc
                .perform(get(url(myWs)).cookie(auth(mine)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())) {
      var txs = book.getSheet("Transactions");
      assertThat(txs.getLastRowNum()).isEqualTo(1);
      assertThat(txs.getRow(1).getCell(8).getStringCellValue()).isEqualTo("mine");
    }

    mockMvc.perform(get(url(myWs)).cookie(auth(other))).andExpect(status().is4xxClientError());
    mockMvc.perform(get(url(myWs))).andExpect(status().isUnauthorized());
  }

  private static XSSFWorkbook open(byte[] bytes) throws IOException {
    return new XSSFWorkbook(new ByteArrayInputStream(bytes));
  }

  private static String url(UUID ws) {
    return "/api/v1/workspaces/" + ws + "/export.xlsx";
  }

  /** POSTs to a workspace path, expects 201 and returns the new id. */
  private String create(Session s, UUID ws, String path, String json) throws Exception {
    ResultActions result =
        mockMvc
            .perform(
                post("/api/v1/workspaces/" + ws + path)
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated());
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }

  private void tx(Session s, UUID ws, String party, long paise, String date, String note)
      throws Exception {
    String cash =
        JsonPath.read(
            mockMvc
                .perform(get("/api/v1/workspaces/" + ws + "/accounts").cookie(auth(s)))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.items[0].id");
    String partyField = party == null ? "" : ", \"party_id\": \"" + party + "\"";
    create(
        s,
        ws,
        "/transactions",
        ("{\"account_id\": \"%s\", \"type\": \"expense\", \"amount_paise\": %d,"
                + " \"occurred_on\": \"%s\", \"note\": \"%s\"%s}")
            .formatted(cash, paise, date, note, partyField));
  }
}
