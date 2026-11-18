import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Account = components["schemas"]["AccountResponse"];
type CreateAccountRequest = components["schemas"]["CreateAccountRequest"];
type UpdateAccountRequest = components["schemas"]["UpdateAccountRequest"];

export const accountsKey = (workspaceId: string) => ["accounts", workspaceId] as const;

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/accounts`;

/** All accounts of a workspace, archived ones included (they still name old transactions). */
export function useAccounts(workspaceId: string) {
  return useQuery({
    queryKey: accountsKey(workspaceId),
    queryFn: async () =>
      (await apiFetch<components["schemas"]["ListResponseAccountResponse"]>(base(workspaceId)))
        .items,
  });
}

export function useCreateAccount(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateAccountRequest) =>
      apiFetch<Account>(base(workspaceId), { method: "POST", json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountsKey(workspaceId) }),
  });
}

/** A PATCH: only the fields sent change. Archiving is `{ archived: true }`. */
export function useUpdateAccount(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateAccountRequest }) =>
      apiFetch<Account>(`${base(workspaceId)}/${id}`, { method: "PATCH", json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountsKey(workspaceId) }),
  });
}
