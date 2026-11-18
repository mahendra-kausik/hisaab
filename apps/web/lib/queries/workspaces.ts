import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { ME_KEY, type WorkspaceSummary } from "./me";

type CreateWorkspaceRequest = components["schemas"]["CreateWorkspaceRequest"];

export function useCreateWorkspace() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateWorkspaceRequest) =>
      apiFetch<WorkspaceSummary>("/api/v1/workspaces", { method: "POST", json: body }),
    // The workspace list lives inside /me, so refetch it.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ME_KEY }),
  });
}
