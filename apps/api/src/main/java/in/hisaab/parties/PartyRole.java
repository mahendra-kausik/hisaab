package in.hisaab.parties;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

public enum PartyRole {
  BUYER,
  SELLER,
  CONTRACTOR,
  BROKER,
  SUPPLIER,
  TENANT,
  FRIEND_FAMILY,
  OTHER;

  // @JsonValue: Jackson writes and reads this enum as its lowercase name ("friend_family").
  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<PartyRole> {
    public DbConverter() {
      super(PartyRole.class);
    }
  }
}
