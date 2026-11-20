import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { partyTotals } from "@/lib/dues";
import { accountsKey } from "./accounts";

export type Due = components["schemas"]["DueResponse"];
type CreateDueRequest = components["schemas"]["CreateDueRequest"];
type UpdateDueRequest = components["schemas"]["UpdateDueRequest"];

export const duesKey = (workspaceId: string) => ["dues", workspaceId] as const;

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/dues`;

/** All dues of a workspace, or of one party when `partyId` is given. Cancelled ones included. */
export function useDues(workspaceId: string, partyId?: string) {
  return useQuery({
    queryKey: [...duesKey(workspaceId), partyId ?? "all"],
    queryFn: async () =>
      (
        await apiFetch<components["schemas"]["ListResponseDueResponse"]>(
          partyId ? `${base(workspaceId)}?party_id=${partyId}` : base(workspaceId),
        )
      ).items,
  });
}

/**
 * Create (no `id`) or replace (`id`, a PUT) a due. A create can carry an `origin`, which also
 * makes a transaction, so the transaction list and account balances are refetched too.
 */
export function useSaveDue(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: CreateDueRequest | UpdateDueRequest }) =>
      apiFetch<Due>(id ? `${base(workspaceId)}/${id}` : base(workspaceId), {
        method: id ? "PUT" : "POST",
        json: body,
      }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: duesKey(workspaceId) }),
        queryClient.invalidateQueries({ queryKey: ["transactions", workspaceId] }),
        queryClient.invalidateQueries({ queryKey: accountsKey(workspaceId) }),
      ]),
  });
}

/** Cancel or reopen a due. Both reverse each other, so neither needs a confirm dialog. */
export function useCancelReopenDue(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: "cancel" | "reopen" }) =>
      apiFetch<Due>(`${base(workspaceId)}/${id}/${action}`, { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: duesKey(workspaceId) }),
  });
}

/**
 * Total paid and received across every transaction with a party. The transactions API is paged,
 * so this follows `next_cursor` 200 rows at a time and sums in the browser.
 * ponytail: fine for a few thousand rows per party; add an API SUM endpoint if it gets slow.
 * The key starts with "transactions", so every transaction change refreshes it.
 */
export function usePartyTotals(workspaceId: string, partyId: string) {
  return useQuery({
    queryKey: ["transactions", workspaceId, "party-totals", partyId],
    queryFn: async () => {
      const items: components["schemas"]["TransactionResponse"][] = [];
      let cursor: string | null | undefined;
      do {
        const params = new URLSearchParams({ party_id: partyId, limit: "200" });
        if (cursor) params.set("cursor", cursor);
        const page = await apiFetch<components["schemas"]["ListResponseTransactionResponse"]>(
          `/api/v1/workspaces/${workspaceId}/transactions?${params}`,
        );
        items.push(...page.items);
        cursor = page.next_cursor;
      } while (cursor);
      return partyTotals(items);
    },
  });
}
