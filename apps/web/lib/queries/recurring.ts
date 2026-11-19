import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type RecurringRule = components["schemas"]["RecurringRuleResponse"];
export type RecurringRuleRequest = components["schemas"]["RecurringRuleRequest"];

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/recurring`;
const key = (workspaceId: string) => ["recurring", workspaceId] as const;

export function useRecurring(workspaceId: string) {
  return useQuery({
    queryKey: key(workspaceId),
    queryFn: async () =>
      (await apiFetch<components["schemas"]["ListResponseRecurringRuleResponse"]>(base(workspaceId)))
        .items,
  });
}

/** POST when `id` is missing, PUT (full replace) when it is there. */
export function useSaveRule(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: RecurringRuleRequest }) =>
      apiFetch<RecurringRule>(id ? `${base(workspaceId)}/${id}` : base(workspaceId), {
        method: id ? "PUT" : "POST",
        json: body,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(workspaceId) }),
  });
}

export function usePauseResume(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: "pause" | "resume" }) =>
      apiFetch<RecurringRule>(`${base(workspaceId)}/${id}/${action}`, { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(workspaceId) }),
  });
}

export function useDeleteRule(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch(`${base(workspaceId)}/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key(workspaceId) }),
  });
}
