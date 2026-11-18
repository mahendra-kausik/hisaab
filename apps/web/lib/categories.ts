import type { components } from "@/lib/api/schema";

type Category = components["schemas"]["CategoryResponse"];

export type CategoryNode = { parent: Category; children: Category[] };

/**
 * One kind's categories as a two-level tree for the management page. Keeps the API's order
 * (kind, sort order, name), so parents and their children appear as the API lists them.
 */
export function categoryTree(categories: Category[], kind: Category["kind"]): CategoryNode[] {
  const ofKind = categories.filter((c) => c.kind === kind);
  return ofKind
    .filter((c) => !c.parent_id)
    .map((parent) => ({ parent, children: ofKind.filter((c) => c.parent_id === parent.id) }));
}
