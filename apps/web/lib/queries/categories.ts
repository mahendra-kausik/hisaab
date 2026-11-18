import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Category = components["schemas"]["CategoryResponse"];

/** All categories of a workspace as a flat list, hidden ones included. */
export function useCategories(workspaceId: string) {
  return useQuery({
    queryKey: ["categories", workspaceId],
    queryFn: async () =>
      (
        await apiFetch<components["schemas"]["ListResponseCategoryResponse"]>(
          `/api/v1/workspaces/${workspaceId}/categories`,
        )
      ).items,
  });
}
