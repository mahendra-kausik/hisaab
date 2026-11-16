package in.hisaab.workspaces;

import java.util.UUID;

/** A workspace together with the current user's role in it (one row of "my workspaces"). */
public record WorkspaceMembership(UUID id, String name, WorkspaceKind kind, WorkspaceRole role) {}
