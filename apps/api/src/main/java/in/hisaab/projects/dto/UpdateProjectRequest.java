package in.hisaab.projects.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH body: any field left out (null) stays unchanged. To clear notes, send an empty string. */
public record UpdateProjectRequest(
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
    @Size(max = 500) String notes,
    Boolean archived) {}
