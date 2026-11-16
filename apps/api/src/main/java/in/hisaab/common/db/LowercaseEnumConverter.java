package in.hisaab.common.db;

import jakarta.persistence.AttributeConverter;

/**
 * Stores an enum in the database as its lowercase name ({@code OWNER} becomes {@code 'owner'}),
 * matching the CHECK constraints in the migrations. Each enum adds a tiny annotated subclass; see
 * {@code WorkspaceKind.Converter}.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>>
    implements AttributeConverter<E, String> {

  private final Class<E> type;

  protected LowercaseEnumConverter(Class<E> type) {
    this.type = type;
  }

  @Override
  public String convertToDatabaseColumn(E value) {
    return value == null ? null : value.name().toLowerCase();
  }

  @Override
  public E convertToEntityAttribute(String column) {
    return column == null ? null : Enum.valueOf(type, column.toUpperCase());
  }
}
