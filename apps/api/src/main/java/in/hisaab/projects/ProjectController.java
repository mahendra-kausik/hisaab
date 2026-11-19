package in.hisaab.projects;

import in.hisaab.common.web.ListResponse;
import in.hisaab.projects.dto.CreateProjectRequest;
import in.hisaab.projects.dto.ProjectResponse;
import in.hisaab.projects.dto.UpdateProjectRequest;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  /** All projects, archived ones included (they have archived_at set). */
  @GetMapping
  public ListResponse<ProjectResponse> list(@PathVariable UUID workspaceId) {
    return ListResponse.of(projectService.list(workspaceId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody CreateProjectRequest request) {
    return projectService.create(workspaceId, request);
  }

  @PatchMapping("/{projectId}")
  public ProjectResponse update(
      @PathVariable UUID workspaceId,
      @PathVariable UUID projectId,
      @Valid @RequestBody UpdateProjectRequest request) {
    return projectService.update(workspaceId, projectId, request);
  }
}
