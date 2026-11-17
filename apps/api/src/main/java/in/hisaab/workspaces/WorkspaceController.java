package in.hisaab.workspaces;

import in.hisaab.users.dto.MeResponse.WorkspaceSummary;
import in.hisaab.workspaces.dto.CreateWorkspaceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  public WorkspaceController(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  /** Creates another workspace (e.g. a business one) with its default account and categories. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceSummary create(@Valid @RequestBody CreateWorkspaceRequest request) {
    var w = workspaceService.create(request.name().trim(), request.kind());
    return new WorkspaceSummary(
        w.getId(), w.getName(), w.getKind().json(), WorkspaceRole.OWNER.name().toLowerCase());
  }
}
