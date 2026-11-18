import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "./client";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("apiFetch", () => {
  it("turns the API error body into an ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        json({ error: { code: "email_taken", message: "Taken", details: null } }, 409),
      ),
    );
    const error = await apiFetch("/api/v1/auth/signup", { method: "POST" }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, code: "email_taken", message: "Taken" });
  });

  it("falls back to a generic error when the body is not the API shape", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("<html>bad gateway</html>", { status: 502 })));
    const error = await apiFetch("/api/v1/me").catch((e) => e);
    expect(error).toMatchObject({ status: 502, code: "unknown_error" });
  });

  it("refreshes once for several parallel 401s, then retries each request", async () => {
    let refreshes = 0;
    let refreshed = false;
    const fetchMock = vi.fn(async (path: string) => {
      if (path === "/api/v1/auth/refresh") {
        refreshes++;
        await new Promise((resolve) => setTimeout(resolve, 10));
        refreshed = true;
        return new Response(null, { status: 204 });
      }
      return refreshed ? json({ ok: path }) : json({ error: { code: "unauthorized" } }, 401);
    });
    vi.stubGlobal("fetch", fetchMock);

    const results = await Promise.all([
      apiFetch<{ ok: string }>("/api/v1/me"),
      apiFetch<{ ok: string }>("/api/v1/workspaces/1/accounts"),
      apiFetch<{ ok: string }>("/api/v1/workspaces/1/tags"),
    ]);

    expect(refreshes).toBe(1);
    expect(results.map((r) => r.ok)).toEqual([
      "/api/v1/me",
      "/api/v1/workspaces/1/accounts",
      "/api/v1/workspaces/1/tags",
    ]);
  });

  it("surfaces the 401 when the refresh fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string) =>
        path === "/api/v1/auth/refresh"
          ? json({ error: { code: "invalid_refresh_token", message: "Sign in again" } }, 401)
          : json({ error: { code: "unauthorized", message: "Authentication required" } }, 401),
      ),
    );
    await expect(apiFetch("/api/v1/me")).rejects.toMatchObject({ status: 401, code: "unauthorized" });
  });

  it("never tries to refresh when a login itself answers 401", async () => {
    const fetchMock = vi.fn(async () =>
      json({ error: { code: "invalid_credentials", message: "Wrong email or password" } }, 401),
    );
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      apiFetch("/api/v1/auth/login", { method: "POST", json: { email: "a@b.in", password: "x" } }),
    ).rejects.toMatchObject({ code: "invalid_credentials" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("returns undefined for 204", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 204 })));
    await expect(apiFetch("/api/v1/auth/logout", { method: "POST" })).resolves.toBeUndefined();
  });
});
