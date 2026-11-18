/** An error answered by the API: `{ "error": { "code", "message", "details" } }`. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: unknown = null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type Options = Omit<RequestInit, "body"> & { json?: unknown };

// All requests share ONE refresh at a time. The API rotates the refresh token on every
// use, so two refreshes with the same token look like token theft and revoke the whole
// session. If several requests get a 401 together, they all wait on this one promise.
let refreshInFlight: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  refreshInFlight ??= fetch("/api/v1/auth/refresh", { method: "POST" })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

async function toApiError(response: Response): Promise<ApiError> {
  let code = "unknown_error";
  let message = `Request failed (${response.status})`;
  let details: unknown = null;
  try {
    // The body is untrusted until narrowed.
    const body: unknown = await response.json();
    const error = (body as { error?: unknown } | null)?.error;
    if (typeof error === "object" && error !== null) {
      const e = error as Record<string, unknown>;
      if (typeof e.code === "string") code = e.code;
      if (typeof e.message === "string") message = e.message;
      details = e.details ?? null;
    }
  } catch {
    // Not JSON (for example a proxy error page): keep the generic message.
  }
  return new ApiError(response.status, code, message, details);
}

function send(path: string, { json, headers, ...init }: Options): Promise<Response> {
  const merged = new Headers(headers);
  if (json !== undefined) merged.set("Content-Type", "application/json");
  return fetch(path, {
    ...init,
    headers: merged,
    body: json === undefined ? undefined : JSON.stringify(json),
  });
}

/**
 * The only way the web app calls the API. Paths are relative (`/api/v1/...`), so Next.js
 * proxies them and the auth cookies stay first-party. On a 401 it refreshes the session
 * once and retries; the auth endpoints themselves are never retried.
 */
export async function apiFetch<T>(path: string, options: Options = {}): Promise<T> {
  let response = await send(path, options);

  if (response.status === 401 && !path.startsWith("/api/v1/auth/")) {
    if (await refreshSession()) {
      response = await send(path, options);
    }
  }

  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
