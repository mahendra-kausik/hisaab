package in.hisaab.common.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The standard list shape: {@code {"items": [...], "next_cursor": null}}. */
public record ListResponse<T>(@NotNull List<T> items, String nextCursor) {

  /** A list that is not paginated (next_cursor is always null). */
  public static <T> ListResponse<T> of(List<T> items) {
    return new ListResponse<>(items, null);
  }
}
