import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Account = components["schemas"]["AccountResponse"];

export const accountsKey = (workspaceId: string) => ["accounts", workspaceId] as const;

/** All accounts of a workspace, archived ones included (they still name old transactions). */
export function useAccounts(workspaceId: string) {
  return useQuery({
    queryKey: accountsKey(workspaceId),
    queryFn: async () =>
      (
        await apiFetch<components["schemas"]["ListResponseAccountResponse"]>(
          `/api/v1/workspaces/${workspaceId}/accounts`,
        )
      ).items,
  });
}
