/**
 * Validates the `?next=` value used after login. Only paths on this site are allowed, so a
 * crafted link cannot send someone to another origin after they sign in.
 */
export function safeNext(value: string | null | undefined): string | null {
  if (!value || !value.startsWith("/")) return null;
  // "//host" and "/\host" are treated by browsers as another origin.
  if (value.startsWith("//") || value.startsWith("/\\")) return null;
  return value;
}
