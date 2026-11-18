import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Me = components["schemas"]["MeResponse"];
export type WorkspaceSummary = components["schemas"]["WorkspaceSummary"];

export const ME_KEY = ["me"] as const;

/** The signed-in user, their settings and workspaces. A 401 here means "not signed in". */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: () => apiFetch<Me>("/api/v1/me"),
  });
}
