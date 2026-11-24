import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Budgets = components["schemas"]["BudgetsResponse"];
export type BudgetItem = components["schemas"]["BudgetItem"];

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/budgets`;
const key = (workspaceId: string) => ["budgets", workspaceId] as const;

/** Budgets and spending for the period containing `day` ("YYYY-MM-DD"); undefined means the current period. */
export function useBudgets(workspaceId: string, day?: string) {
  return useQuery({
    queryKey: [...key(workspaceId), day ?? "current"],
    queryFn: () => apiFetch<Budgets>(`${base(workspaceId)}${day ? `?period=${day}` : ""}`),
  });
}

/**
 * Sets a category's budget from the current period on; 0 removes it. No cache code here: the
 * MutationCache rule in components/providers.tsx refetches the budgets on screen afterwards, so
 * the new row shows what has already been spent.
 */
export function useSetBudget(workspaceId: string) {
  return useMutation({
    mutationFn: ({ categoryId, amountPaise }: { categoryId: string; amountPaise: number }) =>
      apiFetch(`${base(workspaceId)}/${categoryId}`, {
        method: "PUT",
        json: { amount_paise: amountPaise },
      }),
  });
}
