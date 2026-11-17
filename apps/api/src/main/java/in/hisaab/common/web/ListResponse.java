package in.hisaab.common.web;

import java.util.List;

/** The standard list shape: {@code {"items": [...], "next_cursor": null}}. */
public record ListResponse<T>(List<T> items, String nextCursor) {

  /** A list that is not paginated (next_cursor is always null). */
  public static <T> ListResponse<T> of(List<T> items) {
    return new ListResponse<>(items, null);
  }
}
