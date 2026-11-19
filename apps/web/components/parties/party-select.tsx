"use client";

import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/transactions/native-select";
import { useParties } from "@/lib/queries/parties";
import { useWorkspace } from "@/lib/workspace";

/**
 * The optional party picker for transaction and recurring forms. An archived party is listed only
 * while it is the saved choice (`keepId`), the same rule as archived accounts. Hidden until the
 * workspace has a party, so the forms stay short for people who never use parties.
 */
export function PartySelect({
  id,
  value,
  onChange,
  keepId,
}: {
  id: string;
  value: string;
  onChange: (partyId: string) => void;
  keepId?: string;
}) {
  const { active } = useWorkspace();
  const parties = useParties(active.id);
  const options = (parties.data ?? []).filter((p) => !p.archived_at || p.id === keepId);
  if (options.length === 0) return null;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>Party (optional)</Label>
      <NativeSelect id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">No party</option>
        {options.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
            {p.archived_at ? " (archived)" : ""}
          </option>
        ))}
      </NativeSelect>
    </div>
  );
}
