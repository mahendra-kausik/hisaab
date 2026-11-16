package in.hisaab.workspaces;

import java.util.UUID;

/** The composite primary key of {@link WorkspaceMember}; field names must match the entity's. */
public record WorkspaceMemberId(UUID workspaceId, UUID userId) {}
