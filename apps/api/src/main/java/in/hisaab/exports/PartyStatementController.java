package in.hisaab.exports;

import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/parties/{partyId}")
public class PartyStatementController {

  private final PartyStatementService statements;

  public PartyStatementController(PartyStatementService statements) {
    this.statements = statements;
  }

  /** All-time statement for one party as a PDF download. */
  @GetMapping("/statement.pdf")
  public ResponseEntity<byte[]> statement(
      @PathVariable UUID workspaceId, @PathVariable UUID partyId) {
    var file = statements.statement(workspaceId, partyId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.bytes());
  }
}
