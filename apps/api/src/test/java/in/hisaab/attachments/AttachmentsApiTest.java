package in.hisaab.attachments;

import static in.hisaab.TestFixtures.auth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import in.hisaab.PostgresTestBase;
import in.hisaab.TestFixtures;
import in.hisaab.TestFixtures.Session;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AttachmentsApiTest extends PostgresTestBase {

  private static final byte[] PDF = "%PDF-1.4 a receipt".getBytes(StandardCharsets.UTF_8);

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fixtures;

  // Upload, list, download the same bytes with safe headers, delete: the row and the stored file
  // go.
  @Test
  void transactionAttachmentRoundTrip() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String tx = expense(s, ws);

    String id =
        id(
            upload(s, ws, "transactions/" + tx, "receipt.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_name").value("receipt.pdf"))
                .andExpect(jsonPath("$.mime_type").value("application/pdf"))
                .andExpect(jsonPath("$.size_bytes").value(PDF.length))
                // The storage key is private to the server.
                .andExpect(jsonPath("$.object_key").doesNotExist()));
    assertThat(objectCount(prefix(ws))).isEqualTo(1);

    mockMvc
        .perform(get(base(ws) + "/transactions/" + tx + "/attachments").cookie(auth(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id));

    var download =
        mockMvc
            .perform(get(base(ws) + "/attachments/" + id + "/content").cookie(auth(s)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "sandbox"))
            .andReturn()
            .getResponse();
    assertThat(download.getHeader("Content-Disposition"))
        .startsWith("inline")
        .contains("receipt.pdf");
    assertThat(download.getContentAsByteArray()).isEqualTo(PDF);

    mockMvc
        .perform(delete(base(ws) + "/attachments/" + id).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(base(ws) + "/attachments/" + id + "/content").cookie(auth(s)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(base(ws) + "/transactions/" + tx + "/attachments").cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(0));
    assertThat(objectCount(prefix(ws))).isZero();
  }

  @Test
  void dueAttachment() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String due = due(s, ws);
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

    String id =
        id(
            upload(s, ws, "dues/" + due, "agreement photo.jpg", "image/jpeg", jpeg)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_name").value("agreement photo.jpg")));

    mockMvc
        .perform(get(base(ws) + "/dues/" + due + "/attachments").cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1));
    var bytes =
        mockMvc
            .perform(get(base(ws) + "/attachments/" + id + "/content").cookie(auth(s)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(bytes).isEqualTo(jpeg);
  }

  @Test
  void rejectsBadUploads() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String tx = expense(s, ws);
    String target = "transactions/" + tx;

    upload(s, ws, target, "notes.txt", "text/plain", PDF)
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error.code").value("unsupported_type"));
    upload(s, ws, target, "page.html", "text/html", PDF)
        .andExpect(status().isUnsupportedMediaType());
    upload(s, ws, target, "big.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.error.code").value("file_too_large"));
    upload(s, ws, target, "empty.pdf", "application/pdf", new byte[0])
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("empty_file"));
    upload(s, ws, target, "  ", "application/pdf", PDF)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("invalid_file_name"));
    // Exactly 10 MB is allowed.
    upload(s, ws, target, "max.pdf", "application/pdf", new byte[10 * 1024 * 1024])
        .andExpect(status().isCreated());
  }

  @Test
  void transferLegsCannotHaveAttachments() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String cash = cashId(s, ws);
    String bank =
        JsonPath.read(
            mockMvc
                .perform(
                    post(base(ws) + "/accounts")
                        .cookie(auth(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bank\", \"type\": \"bank\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    String transfer =
        mockMvc
            .perform(
                post(base(ws) + "/transfers")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"from_account_id\": \"%s\", \"to_account_id\": \"%s\", \"amount_paise\": 500, \"occurred_on\": \"2026-11-01\"}"
                            .formatted(cash, bank)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String leg = JsonPath.read(transfer, "$.out_transaction_id");

    upload(s, ws, "transactions/" + leg, "r.pdf", "application/pdf", PDF)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("transfer_leg"));
  }

  // A soft-deleted transaction hides its attachments; restoring it brings them back.
  @Test
  void attachmentsFollowTheirParentsLifecycle() throws Exception {
    Session s = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(s);
    String tx = expense(s, ws);
    String id =
        id(
            upload(s, ws, "transactions/" + tx, "r.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated()));

    mockMvc
        .perform(delete(base(ws) + "/transactions/" + tx).cookie(auth(s)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(base(ws) + "/transactions/" + tx + "/attachments").cookie(auth(s)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(base(ws) + "/attachments/" + id + "/content").cookie(auth(s)))
        .andExpect(status().isNotFound());
    upload(s, ws, "transactions/" + tx, "r2.pdf", "application/pdf", PDF)
        .andExpect(status().isNotFound());

    mockMvc
        .perform(post(base(ws) + "/transactions/" + tx + "/restore").cookie(auth(s)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(base(ws) + "/transactions/" + tx + "/attachments").cookie(auth(s)))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  // Cross-workspace access: another user learns nothing and can change nothing.
  @Test
  void otherUsersCannotReachAttachments() throws Exception {
    Session owner = fixtures.signup();
    UUID ws = fixtures.workspaceIdOf(owner);
    String tx = expense(owner, ws);
    String id =
        id(
            upload(owner, ws, "transactions/" + tx, "r.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated()));

    Session intruder = fixtures.signup();
    mockMvc
        .perform(get(base(ws) + "/transactions/" + tx + "/attachments").cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(base(ws) + "/attachments/" + id + "/content").cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(base(ws) + "/attachments/" + id).cookie(auth(intruder)))
        .andExpect(status().isNotFound());
    upload(intruder, ws, "transactions/" + tx, "x.pdf", "application/pdf", PDF)
        .andExpect(status().isNotFound());

    // The attachment id used inside the intruder's own workspace is also unknown.
    UUID ownWs = fixtures.workspaceIdOf(intruder);
    mockMvc
        .perform(get(base(ownWs) + "/attachments/" + id + "/content").cookie(auth(intruder)))
        .andExpect(status().isNotFound());

    // And it is all still there for the owner.
    assertThat(objectCount(prefix(ws))).isEqualTo(1);
    mockMvc
        .perform(get(base(ws) + "/attachments/" + id + "/content").cookie(auth(owner)))
        .andExpect(status().isOk());
  }

  @Test
  void requiresLogin() throws Exception {
    mockMvc
        .perform(get(base(UUID.randomUUID()) + "/attachments/" + UUID.randomUUID() + "/content"))
        .andExpect(status().isUnauthorized());
  }

  private static String base(UUID ws) {
    return "/api/v1/workspaces/" + ws;
  }

  private static String prefix(UUID ws) {
    return "workspaces/" + ws + "/attachments/";
  }

  private ResultActions upload(
      Session s, UUID ws, String parent, String fileName, String contentType, byte[] bytes)
      throws Exception {
    return mockMvc.perform(
        post(base(ws) + "/" + parent + "/attachments")
            .param("file_name", fileName)
            .cookie(auth(s))
            .contentType(contentType)
            .content(bytes));
  }

  private String expense(Session s, UUID ws) throws Exception {
    return id(
        mockMvc
            .perform(
                post(base(ws) + "/transactions")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"account_id\": \"%s\", \"type\": \"expense\", \"amount_paise\": 1000, \"occurred_on\": \"2026-11-01\"}"
                            .formatted(cashId(s, ws))))
            .andExpect(status().isCreated()));
  }

  private String due(Session s, UUID ws) throws Exception {
    String party =
        id(
            mockMvc
                .perform(
                    post(base(ws) + "/parties")
                        .cookie(auth(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Buyer\", \"role\": \"buyer\"}"))
                .andExpect(status().isCreated()));
    return id(
        mockMvc
            .perform(
                post(base(ws) + "/dues")
                    .cookie(auth(s))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"party_id\": \"%s\", \"direction\": \"receivable\", \"amount_paise\": 5000, \"reason\": \"Sale\"}"
                            .formatted(party)))
            .andExpect(status().isCreated()));
  }

  private String cashId(Session s, UUID ws) throws Exception {
    String body =
        mockMvc
            .perform(get(base(ws) + "/accounts").cookie(auth(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.items[0].id");
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }
}
