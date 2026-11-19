package in.hisaab.parties;

import in.hisaab.common.web.ListResponse;
import in.hisaab.parties.dto.CreatePartyRequest;
import in.hisaab.parties.dto.PartyResponse;
import in.hisaab.parties.dto.UpdatePartyRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/parties")
public class PartyController {

  private final PartyService partyService;

  public PartyController(PartyService partyService) {
    this.partyService = partyService;
  }

  /** All parties, archived ones included (they have archived_at set). */
  @GetMapping
  public ListResponse<PartyResponse> list(@PathVariable UUID workspaceId) {
    return ListResponse.of(partyService.list(workspaceId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PartyResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody CreatePartyRequest request) {
    return partyService.create(workspaceId, request);
  }

  @PatchMapping("/{partyId}")
  public PartyResponse update(
      @PathVariable UUID workspaceId,
      @PathVariable UUID partyId,
      @Valid @RequestBody UpdatePartyRequest request) {
    return partyService.update(workspaceId, partyId, request);
  }
}
