package in.hisaab.workspaces;

import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Only changes the default seed data (U3); every feature works in every kind. */
public enum WorkspaceKind {
  PERSONAL,
  BUSINESS;

  // autoApply: JPA uses this for every WorkspaceKind field without further annotations.
  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<WorkspaceKind> {
    public DbConverter() {
      super(WorkspaceKind.class);
    }
  }
}
