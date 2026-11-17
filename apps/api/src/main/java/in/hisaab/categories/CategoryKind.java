package in.hisaab.categories;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Expense and income categories are separate sets. */
public enum CategoryKind {
  EXPENSE,
  INCOME;

  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<CategoryKind> {
    public DbConverter() {
      super(CategoryKind.class);
    }
  }
}
