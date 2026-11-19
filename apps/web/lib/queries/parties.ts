import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type Party = components["schemas"]["PartyResponse"];
export type PartyRole = Party["role"];
type CreatePartyRequest = components["schemas"]["CreatePartyRequest"];
type UpdatePartyRequest = components["schemas"]["UpdatePartyRequest"];

export const PARTY_ROLES: { value: PartyRole; label: string }[] = [
  { value: "buyer", label: "Buyer" },
  { value: "seller", label: "Seller" },
  { value: "contractor", label: "Contractor" },
  { value: "broker", label: "Broker" },
  { value: "supplier", label: "Supplier" },
  { value: "tenant", label: "Tenant" },
  { value: "friend_family", label: "Friend / family" },
  { value: "other", label: "Other" },
];

const partiesKey = (workspaceId: string) => ["parties", workspaceId] as const;

const base = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}/parties`;

/** All parties of a workspace, archived ones included (they still name old transactions). */
export function useParties(workspaceId: string) {
  return useQuery({
    queryKey: partiesKey(workspaceId),
    queryFn: async () =>
      (await apiFetch<components["schemas"]["ListResponsePartyResponse"]>(base(workspaceId))).items,
  });
}

export function useCreateParty(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePartyRequest) =>
      apiFetch<Party>(base(workspaceId), { method: "POST", json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: partiesKey(workspaceId) }),
  });
}

/** A PATCH: only the fields sent change. Archiving is `{ archived: true }`. */
export function useUpdateParty(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdatePartyRequest }) =>
      apiFetch<Party>(`${base(workspaceId)}/${id}`, { method: "PATCH", json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: partiesKey(workspaceId) }),
  });
}
