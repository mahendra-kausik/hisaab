package in.hisaab.transactions;

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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class TransactionExportApiTest extends PostgresTestBase {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  @Test
  void exportsFilteredRowsWithQuotingAndFormulaGuard() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = firstId(s, ws, "accounts", "Cash");

    postTx(s, ws, cash, 12345, "2026-03-10", "Lunch, \\\"special\\\"");
    postTx(s, ws, cash, 500, "2026-03-11", "=SUM(A1)");
    postTx(s, ws, cash, 999, "2026-04-01", "outside range");

    String csv =
        export(s, ws, "?from=2026-03-01&to=2026-03-31")
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"hisaab-2026-03-01-2026-03-31.csv\""))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(csv).startsWith("﻿date,type,amount,account,category,party,payment_method,note,tags");
    assertThat(csv).contains("2026-03-10,expense,123.45,Cash,,,,\"Lunch, \"\"special\"\"\",");
    assertThat(csv).contains(",'=SUM(A1),");
    assertThat(csv).doesNotContain("outside range");
  }

  @Test
  void otherWorkspacesRowsAreNeverIncluded() throws Exception {
    Session mine = fixtures.signup();
    UUID myWs = fixtures.workspaceIdOf(mine);
    postTx(mine, myWs, firstId(mine, myWs, "accounts", "Cash"), 100, "2026-03-10", "mine");

    Session other = fixtures.signup();
    UUID otherWs = fixtures.workspaceIdOf(other);
    postTx(
        other, otherWs, firstId(other, otherWs, "accounts", "Cash"), 200, "2026-03-10", "theirs");

    String csv =
        export(mine, myWs, "").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv).contains("mine").doesNotContain("theirs");

    // Someone who is not a member of the workspace gets nothing.
    mockMvc
        .perform(get("/api/v1/workspaces/" + myWs + "/transactions/export.csv").cookie(auth(other)))
        .andExpect(status().is4xxClientError());
  }

  private org.springframework.test.web.servlet.ResultActions export(
      Session s, UUID ws, String query) throws Exception {
    return mockMvc.perform(
        get("/api/v1/workspaces/" + ws + "/transactions/export.csv" + query).cookie(auth(s)));
  }

  private void postTx(Session s, UUID ws, String accountId, long paise, String date, String note)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspaces/" + ws + "/transactions")
                .cookie(auth(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"account_id\": \"%s\", \"type\": \"expense\", \"amount_paise\": %d,"
                            + " \"occurred_on\": \"%s\", \"note\": \"%s\"}")
                        .formatted(accountId, paise, date, note)))
        .andExpect(status().isCreated());
  }

  private String firstId(Session s, UUID ws, String path, String name) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/workspaces/" + ws + "/" + path).cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(body, "$.items[?(@.name == '%s')].id".formatted(name));
    return ids.getFirst();
  }
}
