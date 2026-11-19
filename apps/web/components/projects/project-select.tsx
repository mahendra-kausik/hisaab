"use client";

import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/transactions/native-select";
import { useProjects } from "@/lib/queries/projects";
import { useWorkspace } from "@/lib/workspace";

/**
 * The optional project picker for transaction and recurring forms. An archived project is listed
 * only while it is the saved choice (`keepId`), the same rule as archived accounts. Hidden until
 * the workspace has a project, so the forms stay short for people who never use projects.
 */
export function ProjectSelect({
  id,
  value,
  onChange,
  keepId,
}: {
  id: string;
  value: string;
  onChange: (projectId: string) => void;
  keepId?: string;
}) {
  const { active } = useWorkspace();
  const projects = useProjects(active.id);
  const options = (projects.data ?? []).filter((p) => !p.archived_at || p.id === keepId);
  if (options.length === 0) return null;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>Project (optional)</Label>
      <NativeSelect id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">No project</option>
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
