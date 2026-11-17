package in.hisaab.transactions;

import in.hisaab.transactions.dto.TransactionFilter;
import in.hisaab.transactions.dto.TransactionResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The read side: hand-written SQL, because the list has optional filters, a keyset cursor and each
 * row's tags, which is clearer in SQL than in JPA. Runs inside the service's transaction, so RLS
 * applies to every statement here too.
 */
@Repository
public class TransactionQueries {

  private static final String SELECT =
      """
      SELECT t.id, t.account_id, t.type, t.amount_paise, t.occurred_on, t.category_id,
             t.payment_method, t.note, t.source, t.created_at, t.updated_at,
             array(SELECT g.name FROM transaction_tags tt JOIN tags g ON g.id = tt.tag_id
                   WHERE tt.transaction_id = t.id ORDER BY lower(g.name)) AS tags
      FROM transactions t
      """;

  private static final RowMapper<TransactionResponse> MAPPER = TransactionQueries::mapRow;

  private final JdbcTemplate jdbc;

  public TransactionQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<TransactionResponse> findById(UUID workspaceId, UUID id) {
    return jdbc
        .query(
            SELECT + " WHERE t.workspace_id = ? AND t.id = ? AND t.deleted_at IS NULL",
            MAPPER,
            workspaceId,
            id)
        .stream()
        .findFirst();
  }

  /**
   * Newest first. Asks for {@code limit} rows; the caller passes one extra to detect a next page.
   */
  public List<TransactionResponse> page(
      UUID workspaceId, TransactionFilter filter, TransactionCursor cursor, int limit) {
    var sql =
        new StringBuilder(SELECT).append(" WHERE t.workspace_id = ? AND t.deleted_at IS NULL");
    List<Object> args = new ArrayList<>();
    args.add(workspaceId);

    if (filter.from() != null) {
      sql.append(" AND t.occurred_on >= ?");
      args.add(filter.from());
    }
    if (filter.to() != null) {
      sql.append(" AND t.occurred_on <= ?");
      args.add(filter.to());
    }
    if (filter.type() != null) {
      sql.append(" AND t.type = ?");
      args.add(filter.type().json());
    }
    if (filter.accountId() != null) {
      sql.append(" AND t.account_id = ?");
      args.add(filter.accountId());
    }
    if (filter.categoryId() != null) {
      // The category itself or any of its children, so filtering by "Food" includes "Groceries".
      sql.append(
          " AND t.category_id IN (SELECT c.id FROM categories c"
              + " WHERE c.workspace_id = ? AND (c.id = ? OR c.parent_id = ?))");
      args.addAll(Arrays.asList(workspaceId, filter.categoryId(), filter.categoryId()));
    }
    if (filter.tag() != null) {
      sql.append(
          " AND EXISTS (SELECT 1 FROM transaction_tags tt JOIN tags g ON g.id = tt.tag_id"
              + " WHERE tt.transaction_id = t.id AND lower(g.name) = lower(?))");
      args.add(filter.tag());
    }
    if (filter.q() != null) {
      sql.append(" AND t.note ILIKE ? ESCAPE '\\'");
      args.add("%" + escapeLike(filter.q()) + "%");
    }
    if (cursor != null) {
      // Row comparison: "sorts after the cursor row" in all three sort columns at once.
      sql.append(" AND (t.occurred_on, t.created_at, t.id) < (?::date, ?::timestamptz, ?::uuid)");
      args.add(cursor.occurredOn());
      args.add(OffsetDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC));
      args.add(cursor.id());
    }
    sql.append(" ORDER BY t.occurred_on DESC, t.created_at DESC, t.id DESC LIMIT ?");
    args.add(limit);

    return jdbc.query(sql.toString(), MAPPER, args.toArray());
  }

  /** Makes the user's search text literal: % and _ are LIKE wildcards, \ is the escape. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static TransactionResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
    String paymentMethod = rs.getString("payment_method");
    return new TransactionResponse(
        rs.getObject("id", UUID.class),
        rs.getObject("account_id", UUID.class),
        TransactionType.valueOf(rs.getString("type").toUpperCase()),
        rs.getLong("amount_paise"),
        rs.getObject("occurred_on", LocalDate.class),
        rs.getObject("category_id", UUID.class),
        paymentMethod == null ? null : PaymentMethod.valueOf(paymentMethod.toUpperCase()),
        rs.getString("note"),
        List.of((String[]) rs.getArray("tags").getArray()),
        rs.getString("source"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}
