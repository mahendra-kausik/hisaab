import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Category = components["schemas"]["CategoryResponse"];
type CreateCategoryRequest = components["schemas"]["CreateCategoryRequest"];
type UpdateCategoryRequest = components["schemas"]["UpdateCategoryRequest"];

export const categoriesKey = (workspaceId: string) => ["categories", workspaceId] as const;

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/categories`;

/** All categories of a workspace as a flat list, hidden ones included. */
export function useCategories(workspaceId: string) {
  return useQuery({
    queryKey: categoriesKey(workspaceId),
    queryFn: async () =>
      (await apiFetch<components["schemas"]["ListResponseCategoryResponse"]>(base(workspaceId)))
        .items,
  });
}

// The mutations below need no cache code: every successful mutation refetches the pages on
// screen (the MutationCache rule in components/providers.tsx).
export function useCreateCategory(workspaceId: string) {
  return useMutation({
    mutationFn: (body: CreateCategoryRequest) =>
      apiFetch<Category>(base(workspaceId), { method: "POST", json: body }),
  });
}

/** A PATCH: rename with `{ name }`, hide or show with `{ hidden }`. */
export function useUpdateCategory(workspaceId: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateCategoryRequest }) =>
      apiFetch<Category>(`${base(workspaceId)}/${id}`, { method: "PATCH", json: body }),
  });
}

/** Permanent. The API refuses a default category (409 `system_category`) or one in use (409 `category_in_use`). */
export function useDeleteCategory(workspaceId: string) {
  return useMutation({
    mutationFn: (id: string) => apiFetch(`${base(workspaceId)}/${id}`, { method: "DELETE" }),
  });
}
