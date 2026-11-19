package in.hisaab.recurring;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

public enum Frequency {
  WEEKLY,
  MONTHLY,
  YEARLY;

  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<Frequency> {
    public DbConverter() {
      super(Frequency.class);
    }
  }
}
