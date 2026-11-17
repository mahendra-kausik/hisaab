package in.hisaab.transactions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tags are plain names; this class creates them on first use and links them to transactions. */
@Repository
public class TagRepository {

  private final JdbcTemplate jdbc;

  public TagRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Returns the id of each named tag, creating the ones that do not exist yet. Names match ignoring
   * case ("Rent" and "rent" are one tag); {@code names} must already be de-duplicated that way.
   */
  public List<UUID> upsert(UUID workspaceId, List<String> names) {
    List<UUID> ids = new ArrayList<>();
    for (String name : names) {
      // DO NOTHING when the tag exists, so two requests adding the same new tag cannot fail.
      jdbc.update(
          "INSERT INTO tags (workspace_id, name) VALUES (?, ?)"
              + " ON CONFLICT (workspace_id, lower(name)) DO NOTHING",
          workspaceId,
          name);
      ids.add(
          jdbc.queryForObject(
              "SELECT id FROM tags WHERE workspace_id = ? AND lower(name) = lower(?)",
              UUID.class,
              workspaceId,
              name));
    }
    return ids;
  }

  /** Makes the transaction's tags exactly {@code tagIds}. */
  public void replaceLinks(UUID workspaceId, UUID transactionId, List<UUID> tagIds) {
    jdbc.update("DELETE FROM transaction_tags WHERE transaction_id = ?", transactionId);
    jdbc.batchUpdate(
        "INSERT INTO transaction_tags (workspace_id, transaction_id, tag_id) VALUES (?, ?, ?)",
        tagIds,
        tagIds.size(),
        (ps, tagId) -> {
          ps.setObject(1, workspaceId);
          ps.setObject(2, transactionId);
          ps.setObject(3, tagId);
        });
  }

  public List<String> listNames(UUID workspaceId) {
    return jdbc.queryForList(
        "SELECT name FROM tags WHERE workspace_id = ? ORDER BY lower(name)",
        String.class,
        workspaceId);
  }
}
