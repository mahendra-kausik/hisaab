package in.hisaab.projects.dto;

import in.hisaab.projects.Project;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
    @NotNull UUID id, @NotNull String name, String notes, Instant archivedAt) {

  public static ProjectResponse from(Project p) {
    return new ProjectResponse(p.getId(), p.getName(), p.getNotes(), p.getArchivedAt());
  }
}
