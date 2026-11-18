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
import { ApiError } from "@/lib/api/client";
import {
  useCreateCategory,
  useUpdateCategory,
  type Category,
} from "@/lib/queries/categories";
import { useWorkspace } from "@/lib/workspace";

/** What the dialog is for: a new category (top level, or under `parent`) or renaming one. */
export type CategoryTarget =
  | { mode: "add"; kind: Category["kind"]; parent?: Category }
  | { mode: "rename"; category: Category };

const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(100, "Use at most 100 characters"),
});

/** Takes the name only: the kind and the parent come from where it was opened. Mount it only while open. */
export function CategoryDialog({
  target,
  onClose,
}: {
  target: CategoryTarget;
  onClose: () => void;
}) {
  const { active } = useWorkspace();
  const create = useCreateCategory(active.id);
  const update = useUpdateCategory(active.id);
  const [error, setError] = useState<string | null>(null);
  const pending = create.isPending || update.isPending;

  const title =
    target.mode === "rename"
      ? "Rename category"
      : target.parent
        ? `New subcategory of ${target.parent.name}`
        : `New ${target.kind} category`;

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = schema.safeParse(Object.fromEntries(new FormData(event.currentTarget)));
    if (!parsed.success) return setError(parsed.error.issues[0].message);
    setError(null);

    const callbacks = {
      onSuccess: () => {
        toast(target.mode === "rename" ? "Category renamed" : "Category added");
        onClose();
      },
      onError: (e: Error) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    };
    if (target.mode === "rename") {
      update.mutate({ id: target.category.id, body: { name: parsed.data.name } }, callbacks);
    } else {
      create.mutate(
        { name: parsed.data.name, kind: target.kind, parent_id: target.parent?.id },
        callbacks,
      );
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="rounded-2xl sm:max-w-md">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription className="sr-only">Enter a name and press Enter to save.</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="category-name">Name</Label>
            <Input
              id="category-name"
              name="name"
              defaultValue={target.mode === "rename" ? target.category.name : ""}
              autoComplete="off"
              autoFocus
              className="h-11"
              aria-invalid={error ? true : undefined}
            />
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
