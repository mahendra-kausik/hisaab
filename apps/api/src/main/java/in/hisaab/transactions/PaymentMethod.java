package in.hisaab.transactions;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

public enum PaymentMethod {
  CASH,
  UPI,
  CARD,
  BANK_TRANSFER,
  CHEQUE,
  OTHER;

  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<PaymentMethod> {
    public DbConverter() {
      super(PaymentMethod.class);
    }
  }
}
