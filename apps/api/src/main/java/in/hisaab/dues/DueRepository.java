package in.hisaab.dues;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DueRepository extends JpaRepository<Due, UUID> {

  /** One row of {@link #paidByWorkspace}. The quoted aliases below name these getters. */
  interface Paid {
    UUID getDueId();

    long getPaidPaise();
  }

  @Query(
      "select d from Due d where d.id = :id and d.workspaceId = :workspaceId and d.deletedAt is null")
  Optional<Due> findLive(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

  // FOR UPDATE: payments and edits on one due take turns, so two simultaneous payments cannot
  // both pass the overpayment check.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from Due d where d.id = :id and d.workspaceId = :workspaceId and d.deletedAt is null")
  Optional<Due> lockLive(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

  // Order: due date (none last), then oldest first. Native because of NULLS LAST and created_at,
  // which the entity does not map.
  @Query(
      value =
          "SELECT * FROM dues WHERE workspace_id = :workspaceId AND deleted_at IS NULL"
              + " ORDER BY due_date NULLS LAST, created_at, id",
      nativeQuery = true)
  List<Due> listLive(@Param("workspaceId") UUID workspaceId);

  @Query(
      value =
          "SELECT * FROM dues WHERE workspace_id = :workspaceId AND party_id = :partyId"
              + " AND deleted_at IS NULL ORDER BY due_date NULLS LAST, created_at, id",
      nativeQuery = true)
  List<Due> listLiveForParty(
      @Param("workspaceId") UUID workspaceId, @Param("partyId") UUID partyId);

  // Paid = live transactions linked to the due. Dues without payments have no row (paid = 0).
  @Query(
      value =
          """
          SELECT due_id AS "dueId", SUM(amount_paise)::bigint AS "paidPaise"
          FROM transactions
          WHERE workspace_id = :workspaceId AND due_id IS NOT NULL AND deleted_at IS NULL
          GROUP BY due_id
          """,
      nativeQuery = true)
  List<Paid> paidByWorkspace(@Param("workspaceId") UUID workspaceId);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(amount_paise), 0)::bigint
          FROM transactions
          WHERE due_id = :dueId AND deleted_at IS NULL
          """,
      nativeQuery = true)
  long paidFor(@Param("dueId") UUID dueId);
}
