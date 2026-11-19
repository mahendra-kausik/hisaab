package in.hisaab.transactions;

import in.hisaab.common.web.ListResponse;
import in.hisaab.common.web.ValidationException;
import in.hisaab.transactions.dto.TransactionFilter;
import in.hisaab.transactions.dto.TransactionRequest;
import in.hisaab.transactions.dto.TransactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/transactions")
public class TransactionController {

  private final TransactionService transactionService;

  private final TransactionExportService exports;

  public TransactionController(
      TransactionService transactionService, TransactionExportService exports) {
    this.transactionService = transactionService;
    this.exports = exports;
  }

  // Query parameter names are spelled out because Jackson's snake_case setting only applies to
  // JSON bodies. @Min/@Max on parameters are checked by Spring MVC and answer 400 when violated.
  @GetMapping
  public ListResponse<TransactionResponse> list(
      @PathVariable UUID workspaceId,
      @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(name = "type", required = false) String type,
      @RequestParam(name = "account_id", required = false) UUID accountId,
      @RequestParam(name = "category_id", required = false) UUID categoryId,
      @RequestParam(name = "party_id", required = false) UUID partyId,
      @RequestParam(name = "tag", required = false) String tag,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(200) int limit) {
    var filter =
        new TransactionFilter(
            from,
            to,
            parseType(type),
            accountId,
            categoryId,
            partyId,
            blankToNull(tag),
            blankToNull(q));
    return transactionService.list(workspaceId, filter, blankToNull(cursor), limit);
  }

  // Same filters as list, no paging. The BOM makes Excel read the file as UTF-8 (₹, Hindi notes).
  // A literal path beats the {transactionId} pattern below, so "export.csv" is never an id.
  @GetMapping("/export.csv")
  public ResponseEntity<byte[]> exportCsv(
      @PathVariable UUID workspaceId,
      @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(name = "type", required = false) String type,
      @RequestParam(name = "account_id", required = false) UUID accountId,
      @RequestParam(name = "category_id", required = false) UUID categoryId,
      @RequestParam(name = "party_id", required = false) UUID partyId,
      @RequestParam(name = "tag", required = false) String tag,
      @RequestParam(name = "q", required = false) String q) {
    var filter =
        new TransactionFilter(
            from,
            to,
            parseType(type),
            accountId,
            categoryId,
            partyId,
            blankToNull(tag),
            blankToNull(q));
    byte[] body = ("﻿" + exports.csv(workspaceId, filter)).getBytes(StandardCharsets.UTF_8);
    String name = "hisaab-" + (from == null ? "start" : from) + "-" + (to == null ? "now" : to);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
        .body(body);
  }

  @GetMapping("/{transactionId}")
  public TransactionResponse get(@PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
    return transactionService.get(workspaceId, transactionId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody TransactionRequest request) {
    return transactionService.create(workspaceId, request);
  }

  @PutMapping("/{transactionId}")
  public TransactionResponse replace(
      @PathVariable UUID workspaceId,
      @PathVariable UUID transactionId,
      @Valid @RequestBody TransactionRequest request) {
    return transactionService.replace(workspaceId, transactionId, request);
  }

  @DeleteMapping("/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
    transactionService.delete(workspaceId, transactionId);
  }

  @PostMapping("/{transactionId}/restore")
  public TransactionResponse restore(
      @PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
    return transactionService.restore(workspaceId, transactionId);
  }

  // Spring's own String-to-enum conversion wants "EXPENSE"; the API speaks lowercase ("expense").
  private static TransactionType parseType(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    for (var candidate : TransactionType.values()) {
      if (candidate.json().equals(type)) {
        return candidate;
      }
    }
    throw new ValidationException(
        "invalid_type",
        "Unknown transaction type",
        Map.of("fields", Map.of("type", "Must be expense, income, transfer_out or transfer_in")));
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
