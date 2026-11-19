package in.hisaab.dues;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** RECEIVABLE: they owe the user. PAYABLE: the user owes them. */
public enum DueDirection {
  RECEIVABLE,
  PAYABLE;

  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<DueDirection> {
    public DbConverter() {
      super(DueDirection.class);
    }
  }
}
