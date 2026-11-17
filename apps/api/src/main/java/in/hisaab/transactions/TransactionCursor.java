package in.hisaab.transactions;

import in.hisaab.common.web.ValidationException;
import in.hisaab.transactions.dto.TransactionResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Where the previous page ended: the sort key of its last row. The next page is everything strictly
 * after it in {@code occurred_on DESC, created_at DESC, id DESC} order (keyset pagination), which
 * stays correct when rows are added or deleted between requests, unlike OFFSET. Clients treat the
 * encoded string as opaque.
 */
public record TransactionCursor(LocalDate occurredOn, Instant createdAt, UUID id) {

  public static TransactionCursor after(TransactionResponse last) {
    return new TransactionCursor(last.occurredOn(), last.createdAt(), last.id());
  }

  public String encode() {
    String raw = occurredOn + "|" + createdAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static TransactionCursor decode(String encoded) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      if (parts.length != 3) {
        throw new IllegalArgumentException("wrong number of parts");
      }
      return new TransactionCursor(
          LocalDate.parse(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2]));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw new ValidationException(
          "invalid_cursor",
          "The cursor is not valid",
          Map.of("fields", Map.of("cursor", "Not a valid cursor")));
    }
  }
}
