"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/client";
import { useCreateProject, useUpdateProject, type Project } from "@/lib/queries/projects";
import { useWorkspace } from "@/lib/workspace";

// The same limits as the API (CreateProjectRequest), so most mistakes are caught before a request.
const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(100, "Use at most 100 characters"),
  notes: z.string().trim().max(500, "Use at most 500 characters"),
});

/** Add (`editing` is null) or edit a project. Mount it only while it is open, so the form starts fresh. */
export function ProjectDialog({
  editing,
  onClose,
}: {
  editing: Project | null;
  onClose: () => void;
}) {
  const { active } = useWorkspace();
  const create = useCreateProject(active.id);
  const update = useUpdateProject(active.id);
  const [error, setError] = useState<string | null>(null);
  const pending = create.isPending || update.isPending;

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = schema.safeParse(Object.fromEntries(new FormData(event.currentTarget)));
    if (!parsed.success) return setError(parsed.error.issues[0].message);

    setError(null);
    // An empty string clears notes on edit; on create the API stores it as null.
    const body = parsed.data;
    const callbacks = {
      onSuccess: () => {
        toast(editing ? "Project updated" : "Project added");
        onClose();
      },
      onError: (e: Error) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    };
    if (editing) update.mutate({ id: editing.id, body }, callbacks);
    else create.mutate(body, callbacks);
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="rounded-2xl sm:max-w-md">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit project" : "Add project"}</DialogTitle>
            <DialogDescription>
              A property or site that money is spent on or earned from, such as a plot or flat.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="project-name">Name</Label>
            <Input
              id="project-name"
              name="name"
              defaultValue={editing?.name}
              autoComplete="off"
              autoFocus
              placeholder="Plot 14, Whitefield"
              className="h-11"
              aria-invalid={error ? true : undefined}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="project-notes">Notes (optional)</Label>
            <Textarea id="project-notes" name="notes" defaultValue={editing?.notes} rows={3} />
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" className="h-11" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" className="h-11" disabled={pending}>
              {pending ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
