import { useInfiniteQuery, useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import type { Row } from "@/lib/transactions";

type TransactionRequest = components["schemas"]["TransactionRequest"];
type TransferRequest = components["schemas"]["TransferRequest"];
type TransferResponse = components["schemas"]["TransferResponse"];

/** The list filters, exactly as the API names them. Empty or missing means "no filter". */
export type TransactionFilters = {
  q?: string;
  type?: string;
  account_id?: string;
  category_id?: string;
  party_id?: string;
  project_id?: string;
  tag?: string;
  from?: string;
  to?: string;
};

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}`;

/** Transfers have their own endpoints; a transfer row's id is the transfer's id. */
function entryPath(workspaceId: string, row: Pick<Row, "kind" | "id">) {
  const resource = row.kind === "transfer" ? "transfers" : "transactions";
  return `${base(workspaceId)}/${resource}/${row.id}`;
}

/** Pages of the list, newest first. "Load more" calls `fetchNextPage`. */
export function useTransactions(workspaceId: string, filters: TransactionFilters) {
  return useInfiniteQuery({
    queryKey: ["transactions", workspaceId, filters],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams();
      for (const [name, value] of Object.entries(filters)) if (value) params.set(name, value);
      if (pageParam) params.set("cursor", pageParam);
      return apiFetch<components["schemas"]["ListResponseTransactionResponse"]>(
        `${base(workspaceId)}/transactions?${params}`,
      );
    },
    // The API sends null (not a missing key) on the last page.
    getNextPageParam: (lastPage) => lastPage.next_cursor ?? undefined,
  });
}

/** One whole transfer, for editing a row that only has one of its two legs loaded. */
export function useTransfer(workspaceId: string, transferId: string, enabled: boolean) {
  return useQuery({
    queryKey: ["transfer", workspaceId, transferId],
    queryFn: () => apiFetch<TransferResponse>(`${base(workspaceId)}/transfers/${transferId}`),
    enabled,
  });
}

// Every change here moves the list, balances, budgets, reports and dues. None of that is listed:
// each successful mutation refetches whatever is on screen (components/providers.tsx).

/** Create (no `id`) or replace (`id`, a PUT) an expense or income. */
export function useSaveTransaction(workspaceId: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: TransactionRequest }) =>
      apiFetch(`${base(workspaceId)}/transactions${id ? `/${id}` : ""}`, {
        method: id ? "PUT" : "POST",
        json: body,
      }),
  });
}

/** Create (no `id`) or replace (`id`, a PUT) a transfer. */
export function useSaveTransfer(workspaceId: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: TransferRequest }) =>
      apiFetch(`${base(workspaceId)}/transfers${id ? `/${id}` : ""}`, {
        method: id ? "PUT" : "POST",
        json: body,
      }),
  });
}

/** Soft delete: the row is hidden, not erased, so `useRestoreEntry` can bring it back. */
export function useDeleteEntry(workspaceId: string) {
  return useMutation({
    mutationFn: (row: Pick<Row, "kind" | "id">) =>
      apiFetch(entryPath(workspaceId, row), { method: "DELETE" }),
  });
}

/** The Undo of a delete. */
export function useRestoreEntry(workspaceId: string) {
  return useMutation({
    mutationFn: (row: Pick<Row, "kind" | "id">) =>
      apiFetch(`${entryPath(workspaceId, row)}/restore`, { method: "POST" }),
  });
}
