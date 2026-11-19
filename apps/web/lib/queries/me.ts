import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Me = components["schemas"]["MeResponse"];
export type WorkspaceSummary = components["schemas"]["WorkspaceSummary"];

export type UpdateSettingsRequest = components["schemas"]["UpdateSettingsRequest"];

export const ME_KEY = ["me"] as const;

/** A PATCH: only the fields sent change. The answer is the whole updated user, so seed the cache. */
export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateSettingsRequest) =>
      apiFetch<Me>("/api/v1/me", { method: "PATCH", json: body }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  });
}

/** The signed-in user, their settings and workspaces. A 401 here means "not signed in". */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: () => apiFetch<Me>("/api/v1/me"),
  });
}
