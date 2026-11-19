package in.hisaab.recurring;

import in.hisaab.common.web.ListResponse;
import in.hisaab.recurring.dto.RecurringRuleRequest;
import in.hisaab.recurring.dto.RecurringRuleResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/recurring")
public class RecurringController {

  private final RecurringService service;

  public RecurringController(RecurringService service) {
    this.service = service;
  }

  @GetMapping
  public ListResponse<RecurringRuleResponse> list(@PathVariable UUID workspaceId) {
    return service.list(workspaceId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RecurringRuleResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody RecurringRuleRequest request) {
    return service.create(workspaceId, request);
  }

  @PutMapping("/{ruleId}")
  public RecurringRuleResponse replace(
      @PathVariable UUID workspaceId,
      @PathVariable UUID ruleId,
      @Valid @RequestBody RecurringRuleRequest request) {
    return service.replace(workspaceId, ruleId, request);
  }

  @PostMapping("/{ruleId}/pause")
  public RecurringRuleResponse pause(@PathVariable UUID workspaceId, @PathVariable UUID ruleId) {
    return service.pause(workspaceId, ruleId);
  }

  @PostMapping("/{ruleId}/resume")
  public RecurringRuleResponse resume(@PathVariable UUID workspaceId, @PathVariable UUID ruleId) {
    return service.resume(workspaceId, ruleId);
  }

  @DeleteMapping("/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID workspaceId, @PathVariable UUID ruleId) {
    service.delete(workspaceId, ruleId);
  }
}
