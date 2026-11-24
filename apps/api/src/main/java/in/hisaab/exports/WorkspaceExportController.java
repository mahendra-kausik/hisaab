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
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class WorkspaceExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final WorkspaceExportService exports;

  public WorkspaceExportController(WorkspaceExportService exports) {
    this.exports = exports;
  }

  /** Every transaction and due in the workspace, all time, as an Excel download. */
  @GetMapping("/export.xlsx")
  public ResponseEntity<byte[]> export(@PathVariable UUID workspaceId) {
    var file = exports.export(workspaceId);
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.bytes());
  }
}
