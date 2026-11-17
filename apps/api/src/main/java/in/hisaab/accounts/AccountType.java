package in.hisaab.accounts;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

public enum AccountType {
  CASH,
  BANK,
  CREDIT_CARD,
  WALLET;

  // @JsonValue: Jackson writes and reads this enum as its lowercase name ("credit_card").
  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<AccountType> {
    public DbConverter() {
      super(AccountType.class);
    }
  }
}
