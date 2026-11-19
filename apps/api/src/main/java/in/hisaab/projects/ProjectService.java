package in.hisaab.projects;

import in.hisaab.common.web.ConflictException;
import in.hisaab.common.web.NotFoundException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.projects.dto.CreateProjectRequest;
import in.hisaab.projects.dto.ProjectResponse;
import in.hisaab.projects.dto.UpdateProjectRequest;
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
public class ProjectService {

  private final ProjectRepository projects;
  private final WorkspaceAccess access;
  private final Clock clock;

  public ProjectService(ProjectRepository projects, WorkspaceAccess access, Clock clock) {
    this.projects = projects;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ProjectResponse> list(UUID workspaceId) {
    access.require(workspaceId, WorkspaceRole.VIEWER);
    return projects.findByWorkspaceIdOrderByName(workspaceId).stream()
        .map(ProjectResponse::from)
        .toList();
  }

  /**
   * For transactions and recurring rules: the project must belong to this workspace and not be
   * archived. Not being in the workspace looks the same as not existing. Called after the caller's
   * own access check.
   */
  @Transactional(readOnly = true)
  public void requireOpen(UUID workspaceId, UUID projectId) {
    var project =
        projects
            .findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() ->
                new ValidationException(
                    "invalid_project",
                    "Project not found in this workspace",
                    Map.of("fields", Map.of("project_id", "Not a project in this workspace"))));
    if (project.getArchivedAt() != null) {
      throw new ValidationException(
          "project_archived",
          "This project is archived",
          Map.of("fields", Map.of("project_id", "Project is archived")));
    }
  }

  @Transactional
  public ProjectResponse create(UUID workspaceId, CreateProjectRequest request) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var project =
        new Project(
            workspaceId, request.name().trim(), blankToNull(request.notes()), Instant.now(clock));
    return ProjectResponse.from(saveUnique(project));
  }

  @Transactional
  public ProjectResponse update(UUID workspaceId, UUID projectId, UpdateProjectRequest patch) {
    access.require(workspaceId, WorkspaceRole.EDITOR);
    var project =
        projects
            .findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new NotFoundException("Project not found"));
    var now = Instant.now(clock);
    // Null means "not in the request": keep the current value. An empty string clears it.
    project.update(
        patch.name() != null ? patch.name().trim() : project.getName(),
        patch.notes() != null ? blankToNull(patch.notes()) : project.getNotes(),
        now);
    if (patch.archived() != null) {
      project.setArchived(patch.archived(), now);
    }
    return ProjectResponse.from(saveUnique(project));
  }

  // saveAndFlush sends the SQL now, so the unique-name violation becomes a 409 here rather than a
  // 500 at commit.
  private Project saveUnique(Project project) {
    try {
      return projects.saveAndFlush(project);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("name_taken", "A project with this name already exists");
    }
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
