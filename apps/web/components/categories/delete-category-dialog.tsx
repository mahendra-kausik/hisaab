"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ApiError } from "@/lib/api/client";
import { useDeleteCategory, type Category } from "@/lib/queries/categories";
import { useWorkspace } from "@/lib/workspace";

/**
 * A confirm dialog, because a category delete cannot be undone. If the API refuses (the category
 * is in use, or has subcategories) its message is shown here and the dialog stays open.
 */
export function DeleteCategoryDialog({
  category,
  onClose,
}: {
  category: Category;
  onClose: () => void;
}) {
  const { active } = useWorkspace();
  const remove = useDeleteCategory(active.id);
  const [error, setError] = useState<string | null>(null);

  function confirm() {
    setError(null);
    remove.mutate(category.id, {
      onSuccess: () => {
        toast(`${category.name} deleted`);
        onClose();
      },
      onError: (e) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Delete “{category.name}”?</DialogTitle>
          <DialogDescription>
            This cannot be undone. A category that is used by transactions, or that has
            subcategories, cannot be deleted; hide it instead.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" className="h-11" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            className="h-11"
            disabled={remove.isPending}
            onClick={confirm}
          >
            {remove.isPending ? "Deleting…" : "Delete"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
