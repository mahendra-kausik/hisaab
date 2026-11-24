package in.hisaab.parties;

import in.hisaab.common.web.ConflictException;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.parties.dto.CreatePartyRequest;
import in.hisaab.parties.dto.PartyResponse;
import in.hisaab.parties.dto.UpdatePartyRequest;
import in.hisaab.workspaces.WorkspaceAccess;
import in.hisaab.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartyService {

  private final PartyRepository parties;
  private final WorkspaceAccess access;
  private final Clock clock;

  public PartyService(PartyRepository parties, WorkspaceAccess access, Clock clock) {
    this.parties = parties;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<PartyResponse> list(UUID workspaceId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return parties.findByWorkspaceIdOrderByName(workspaceId).stream()
        .map(PartyResponse::from)
        .toList();
  }

  /** One party, archived or not. Another workspace's party is "not found". */
  @Transactional(readOnly = true)
  public PartyResponse get(UUID workspaceId, UUID partyId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return parties
        .findByIdAndWorkspaceId(partyId, workspaceId)
        .map(PartyResponse::from)
        .orElseThrow(() -> new NotFoundException("Party not found"));
  }

  /**
   * For transactions and recurring rules: the party must belong to this workspace and not be
   * archived. Not being in the workspace looks the same as not existing. Called after the caller's
   * own access check.
   */
  @Transactional(readOnly = true)
  public void requireOpen(UUID workspaceId, UUID partyId) {
    var party =
        parties
            .findByIdAndWorkspaceId(partyId, workspaceId)
            .orElseThrow(() ->
                new ValidationException(
                    "invalid_party",
                    "Party not found in this workspace",
                    Map.of("fields", Map.of("party_id", "Not a party in this workspace"))));
    if (party.getArchivedAt() != null) {
      throw new ValidationException(
          "party_archived",
          "This party is archived",
          Map.of("fields", Map.of("party_id", "Party is archived")));
    }
  }

  @Transactional
  public PartyResponse create(UUID workspaceId, CreatePartyRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var party =
        new Party(
            workspaceId,
            request.name().trim(),
            blankToNull(request.phone()),
            request.role(),
            blankToNull(request.notes()),
            Instant.now(clock));
    return PartyResponse.from(saveUnique(party));
  }

  @Transactional
  public PartyResponse update(UUID workspaceId, UUID partyId, UpdatePartyRequest patch) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var party =
        parties
            .findByIdAndWorkspaceId(partyId, workspaceId)
            .orElseThrow(() -> new NotFoundException("Party not found"));
    var now = Instant.now(clock);
    // Null means "not in the request": keep the current value. An empty string clears it.
    party.update(
        patch.name() != null ? patch.name().trim() : party.getName(),
        patch.phone() != null ? blankToNull(patch.phone()) : party.getPhone(),
        patch.role() != null ? patch.role() : party.getRole(),
        patch.notes() != null ? blankToNull(patch.notes()) : party.getNotes(),
        now);
    if (patch.archived() != null) {
      party.setArchived(patch.archived(), now);
    }
    return PartyResponse.from(saveUnique(party));
  }

  // saveAndFlush sends the SQL now, so the unique-name violation becomes a 409 here rather than a
  // 500 at commit.
  private Party saveUnique(Party party) {
    try {
      return parties.saveAndFlush(party);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("name_taken", "A party with this name already exists");
    }
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
