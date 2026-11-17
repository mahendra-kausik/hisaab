package in.hisaab.categories.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH body: any field left out (null) stays unchanged. Kind and parent cannot be changed. */
public record UpdateCategoryRequest(
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
    Boolean hidden) {}
