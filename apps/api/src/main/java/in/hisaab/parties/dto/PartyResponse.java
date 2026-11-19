package in.hisaab.parties.dto;

import in.hisaab.parties.Party;
import in.hisaab.parties.PartyRole;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PartyResponse(
    @NotNull UUID id,
    @NotNull String name,
    String phone,
    @NotNull PartyRole role,
    String notes,
    Instant archivedAt) {

  public static PartyResponse from(Party p) {
    return new PartyResponse(
        p.getId(), p.getName(), p.getPhone(), p.getRole(), p.getNotes(), p.getArchivedAt());
  }
}
