import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

/** The names of every tag used in a workspace. */
export function useTags(workspaceId: string) {
  return useQuery({
    queryKey: ["tags", workspaceId],
    queryFn: async () =>
      (
        await apiFetch<components["schemas"]["ListResponseString"]>(
          `/api/v1/workspaces/${workspaceId}/tags`,
        )
      ).items,
  });
}
