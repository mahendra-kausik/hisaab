"use client";

import { createElement, useState } from "react";
import { Eye, EyeOff, MoreVertical, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { categoryIcon } from "@/lib/category-icons";
import { categoryTree } from "@/lib/categories";
import { useCategories, useUpdateCategory, type Category } from "@/lib/queries/categories";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { CategoryDialog, type CategoryTarget } from "./category-dialog";
import { DeleteCategoryDialog } from "./delete-category-dialog";

type Actions = {
  onRename: (c: Category) => void;
  onAddChild: (parent: Category) => void;
  onDelete: (c: Category) => void;
};

function CategoryRow({
  category,
  parent,
  actions,
}: {
  category: Category;
  parent?: Category;
  actions: Actions;
}) {
  const { active } = useWorkspace();
  const update = useUpdateCategory(active.id);
  const icon = categoryIcon(category, parent);
  const hidden = !!category.hidden_at;
  // A subcategory of a hidden category is left out of the pickers too, so it looks muted as well.
  const muted = hidden || !!parent?.hidden_at;

  function setHidden(value: boolean) {
    update.mutate(
      { id: category.id, body: { hidden: value } },
      {
        onSuccess: () => toast(value ? `${category.name} hidden` : `${category.name} shown`),
        onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not update the category"),
      },
    );
  }

  return (
    <li className={cn("flex min-h-14 items-center gap-3 pl-3", parent && "pl-10")}>
      {/* createElement, because the lint rule rejects <Icon> when the icon comes from a function call. */}
      {createElement(icon, {
        className: "h-5 w-5 shrink-0 text-muted-foreground",
        "aria-hidden": true,
      })}
      <span className={cn("min-w-0 flex-1 truncate text-sm font-medium", muted && "text-muted-foreground")}>
        {category.name}
      </span>
      {hidden && (
        <span className="shrink-0 rounded-md bg-subtle px-1.5 py-0.5 text-xs text-muted-foreground">
          Hidden
        </span>
      )}

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}>
          <MoreVertical className="h-4 w-4" />
          <span className="sr-only">Actions for {category.name}</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem className="min-h-11" onClick={() => actions.onRename(category)}>
            <Pencil className="h-4 w-4" />
            Rename
          </DropdownMenuItem>
          {!parent && (
            <DropdownMenuItem className="min-h-11" onClick={() => actions.onAddChild(category)}>
              <Plus className="h-4 w-4" />
              Add subcategory
            </DropdownMenuItem>
          )}
          <DropdownMenuItem className="min-h-11" onClick={() => setHidden(!hidden)}>
            {hidden ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
            {hidden ? "Show" : "Hide"}
          </DropdownMenuItem>
          {/* Default categories can be renamed or hidden, never deleted. */}
          {!category.is_system && (
            <DropdownMenuItem
              variant="destructive"
              className="min-h-11"
              onClick={() => actions.onDelete(category)}
            >
              <Trash2 className="h-4 w-4" />
              Delete
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  );
}

export function CategoriesView() {
  const { active } = useWorkspace();
  const query = useCategories(active.id);
  const [target, setTarget] = useState<CategoryTarget | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);

  const actions: Actions = {
    onRename: (category) => setTarget({ mode: "rename", category }),
    onAddChild: (parent) => setTarget({ mode: "add", kind: parent.kind, parent }),
    onDelete: setDeleting,
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Categories</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Hidden categories stay on old transactions but no longer appear when you add new ones.
          </p>
        </div>
        <Button variant="outline" className="h-11 gap-2" onClick={() => setTarget({ mode: "add", kind: "expense" })}>
          <Plus className="h-4 w-4" />
          Add category
        </Button>
      </div>

      {query.isError ? (
        <div className="flex flex-col items-start gap-3 py-6">
          <p>We could not load your categories.</p>
          <Button className="h-11" onClick={() => query.refetch()}>
            Try again
          </Button>
        </div>
      ) : query.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : (
        <ul className="divide-y rounded-xl border bg-surface">
          {categoryTree(query.data, "expense").map(({ parent, children }) => (
            <li key={parent.id} className="list-none">
              <ul className="divide-y">
                <CategoryRow category={parent} actions={actions} />
                {children.map((child) => (
                  <CategoryRow key={child.id} category={child} parent={parent} actions={actions} />
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}

      {target && <CategoryDialog target={target} onClose={() => setTarget(null)} />}
      {deleting && <DeleteCategoryDialog category={deleting} onClose={() => setDeleting(null)} />}
    </div>
  );
}
