"use client";

import { useState } from "react";
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
import { ApiError } from "@/lib/api/client";
import { useCreateWorkspace } from "@/lib/queries/workspaces";
import { useWorkspace } from "@/lib/workspace";

const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(100, "Use at most 100 characters"),
  kind: z.enum(["personal", "business"]),
});

export function NewWorkspaceDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { setActive } = useWorkspace();
  const create = useCreateWorkspace();
  const [error, setError] = useState<string | null>(null);

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = schema.safeParse(Object.fromEntries(new FormData(event.currentTarget)));
    if (!parsed.success) {
      setError(parsed.error.issues[0].message);
      return;
    }
    setError(null);
    create.mutate(parsed.data, {
      // The hook refetches /me first, so the new workspace is already in the list here.
      onSuccess: (workspace) => {
        setActive(workspace.id);
        onOpenChange(false);
      },
      onError: (e) => setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-2xl">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>New workspace</DialogTitle>
            <DialogDescription>
              Keep a separate set of accounts and records, for example for a business.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="workspace-name">Name</Label>
            <Input
              id="workspace-name"
              name="name"
              autoComplete="off"
              className="h-11"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? "workspace-error" : undefined}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="workspace-kind">Type</Label>
            {/* A native select: accessible, keyboard friendly, no extra code. */}
            <select
              id="workspace-kind"
              name="kind"
              defaultValue="business"
              className="h-11 w-full rounded-md border border-input bg-transparent px-2.5 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
            >
              <option value="personal">Personal</option>
              <option value="business">Business</option>
            </select>
          </div>

          {error && (
            <p id="workspace-error" role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" className="h-11" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" className="h-11" disabled={create.isPending}>
              {create.isPending ? "Creating…" : "Create workspace"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
