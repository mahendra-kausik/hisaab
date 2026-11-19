import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Project = components["schemas"]["ProjectResponse"];
type CreateProjectRequest = components["schemas"]["CreateProjectRequest"];
type UpdateProjectRequest = components["schemas"]["UpdateProjectRequest"];

const projectsKey = (workspaceId: string) => ["projects", workspaceId] as const;

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/projects`;

/** All projects of a workspace, archived ones included (they still name old transactions). */
export function useProjects(workspaceId: string) {
  return useQuery({
    queryKey: projectsKey(workspaceId),
    queryFn: async () =>
      (await apiFetch<components["schemas"]["ListResponseProjectResponse"]>(base(workspaceId)))
        .items,
  });
}

export function useCreateProject(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateProjectRequest) =>
      apiFetch<Project>(base(workspaceId), { method: "POST", json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectsKey(workspaceId) }),
  });
}

/** A PATCH: only the fields sent change. Archiving is `{ archived: true }`. */
export function useUpdateProject(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateProjectRequest }) =>
      apiFetch<Project>(`${base(workspaceId)}/${id}`, { method: "PATCH", json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectsKey(workspaceId) }),
  });
}
