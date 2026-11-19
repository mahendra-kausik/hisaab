package in.hisaab.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Notes are optional; blank notes are stored as null. */
public record CreateProjectRequest(
    @NotBlank @Size(max = 100) String name, @Size(max = 500) String notes) {}
