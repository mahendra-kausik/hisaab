package in.hisaab.workspaces;

import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Declared strongest first: an owner can do everything an editor can, and so on. */
public enum WorkspaceRole {
  OWNER,
  EDITOR,
  VIEWER;

  /** True if this role is at least as powerful as {@code required}. */
  public boolean includes(WorkspaceRole required) {
    return ordinal() <= required.ordinal();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<WorkspaceRole> {
    public DbConverter() {
      super(WorkspaceRole.class);
    }
  }
}
