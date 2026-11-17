package in.hisaab.workspaces;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Only changes the default seed data (U3); every feature works in every kind. */
public enum WorkspaceKind {
  PERSONAL,
  BUSINESS;

  // JSON uses the lowercase name ("business"), so requests can carry a kind.
  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  // autoApply: JPA uses this for every WorkspaceKind field without further annotations.
  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<WorkspaceKind> {
    public DbConverter() {
      super(WorkspaceKind.class);
    }
  }
}
