package in.hisaab.dues;

import in.hisaab.common.web.ListResponse;
import in.hisaab.dues.dto.CreateDueRequest;
import in.hisaab.dues.dto.DueResponse;
import in.hisaab.dues.dto.UpdateDueRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/dues")
public class DueController {

  private final DueService dueService;

  public DueController(DueService dueService) {
    this.dueService = dueService;
  }

  /** All dues (cancelled and settled included; each has a state), optionally for one party. */
  @GetMapping
  public ListResponse<DueResponse> list(
      @PathVariable UUID workspaceId,
      @RequestParam(name = "party_id", required = false) UUID partyId) {
    return dueService.list(workspaceId, partyId);
  }

  @GetMapping("/{dueId}")
  public DueResponse get(@PathVariable UUID workspaceId, @PathVariable UUID dueId) {
    return dueService.get(workspaceId, dueId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DueResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody CreateDueRequest request) {
    return dueService.create(workspaceId, request);
  }

  @PutMapping("/{dueId}")
  public DueResponse replace(
      @PathVariable UUID workspaceId,
      @PathVariable UUID dueId,
      @Valid @RequestBody UpdateDueRequest request) {
    return dueService.replace(workspaceId, dueId, request);
  }

  @PostMapping("/{dueId}/cancel")
  public DueResponse cancel(@PathVariable UUID workspaceId, @PathVariable UUID dueId) {
    return dueService.cancel(workspaceId, dueId);
  }

  @PostMapping("/{dueId}/reopen")
  public DueResponse reopen(@PathVariable UUID workspaceId, @PathVariable UUID dueId) {
    return dueService.reopen(workspaceId, dueId);
  }
}
