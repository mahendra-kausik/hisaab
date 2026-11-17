package in.hisaab.workspaces.dto;

import in.hisaab.workspaces.WorkspaceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
    @NotBlank @Size(max = 100) String name, @NotNull WorkspaceKind kind) {}
