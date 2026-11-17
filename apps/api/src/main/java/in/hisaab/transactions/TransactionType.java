package in.hisaab.transactions;

import com.fasterxml.jackson.annotation.JsonValue;
import in.hisaab.common.db.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Transfers are two linked rows (out + in) created by U5; this unit only accepts the first two. */
public enum TransactionType {
  EXPENSE,
  INCOME,
  TRANSFER_OUT,
  TRANSFER_IN;

  @JsonValue
  public String json() {
    return name().toLowerCase();
  }

  public boolean isTransfer() {
    return this == TRANSFER_OUT || this == TRANSFER_IN;
  }

  @Converter(autoApply = true)
  public static class DbConverter extends LowercaseEnumConverter<TransactionType> {
    public DbConverter() {
      super(TransactionType.class);
    }
  }
}
