import { useMutation } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import type { WorkspaceSummary } from "./me";

type CreateWorkspaceRequest = components["schemas"]["CreateWorkspaceRequest"];

// The workspace list lives inside /me; the refetch after every mutation (components/providers.tsx)
// brings the new workspace in.
export function useCreateWorkspace() {
  return useMutation({
    mutationFn: (body: CreateWorkspaceRequest) =>
      apiFetch<WorkspaceSummary>("/api/v1/workspaces", { method: "POST", json: body }),
  });
}
