"use client";

import { useMemo } from "react";
import { ArrowRightLeft, MoreVertical, Pencil, Trash2 } from "lucide-react";
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
import { formatDate, todayIn } from "@/lib/dates";
import { formatINR } from "@/lib/format";
import { useAccounts } from "@/lib/queries/accounts";
import { useCategories } from "@/lib/queries/categories";
import { useMe } from "@/lib/queries/me";
import { useParties } from "@/lib/queries/parties";
import { useProjects } from "@/lib/queries/projects";
import {
  useDeleteEntry,
  useRestoreEntry,
  useTransactions,
  type TransactionFilters,
} from "@/lib/queries/transactions";
import { groupByDate, toRows, type Row } from "@/lib/transactions";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { useQuickAdd } from "./quick-add-provider";

/** The signed amount with its colour; the sign and the "Transfer" label mean it is never colour alone. */
function Amount({ row }: { row: Row }) {
  if (row.kind === "transfer") {
    return <span className="text-muted-foreground">{formatINR(row.amountPaise)}</span>;
  }
  const income = row.kind === "income";
  return (
    <span className={income ? "text-money-in" : "text-money-out"}>
      {income ? "+" : "−"}
      {formatINR(row.amountPaise)}
    </span>
  );
}

export function TransactionList({
  filters,
  onClear,
}: {
  filters: TransactionFilters;
  onClear: () => void;
}) {
  const { active } = useWorkspace();
  const me = useMe();
  const { openAdd, openEdit } = useQuickAdd();
  const query = useTransactions(active.id, filters);
  const accounts = useAccounts(active.id);
  const categories = useCategories(active.id);
  const parties = useParties(active.id);
  const projects = useProjects(active.id);
  const deleteEntry = useDeleteEntry(active.id);
  const restoreEntry = useRestoreEntry(active.id);

  const rows = useMemo(
    () => toRows(query.data?.pages.flatMap((p) => p.items) ?? []),
    [query.data],
  );
  const groups = useMemo(() => groupByDate(rows), [rows]);

  const accountName = new Map((accounts.data ?? []).map((a) => [a.id, a.name]));
  const categoryById = new Map((categories.data ?? []).map((c) => [c.id, c]));
  const partyName = new Map((parties.data ?? []).map((p) => [p.id, p.name]));
  const projectName = new Map((projects.data ?? []).map((p) => [p.id, p.name]));
  const today = todayIn(me.data?.timezone ?? "Asia/Kolkata");
  const hasFilters = Object.values(filters).some(Boolean);

  function remove(row: Row) {
    const noun = row.kind === "transfer" ? "Transfer" : "Transaction";
    deleteEntry.mutate(row, {
      onSuccess: () =>
        toast(`${noun} deleted`, {
          duration: 8000,
          action: {
            label: "Undo",
            onClick: () =>
              restoreEntry.mutate(row, { onError: () => toast.error("Could not undo the delete") }),
          },
        }),
      onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not delete"),
    });
  }

  if (query.isError) {
    return (
      <div className="flex flex-col items-start gap-3 py-6">
        <p>We could not load your transactions.</p>
        <Button className="h-11" onClick={() => query.refetch()}>
          Try again
        </Button>
      </div>
    );
  }

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-2" aria-busy="true">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="flex flex-col items-start gap-3 rounded-xl border bg-surface p-6">
        {hasFilters ? (
          <>
            <p>No transactions match these filters.</p>
            <Button variant="outline" className="h-11" onClick={onClear}>
              Clear filters
            </Button>
          </>
        ) : (
          <>
            <p>No transactions yet — Add your first expense.</p>
            <Button className="h-11" onClick={openAdd}>
              Add expense
            </Button>
          </>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {groups.map((group) => (
        <section key={group.date} aria-label={formatDate(group.date, today)}>
          <header className="mb-2 flex items-baseline justify-between px-1">
            <h2 className="text-sm font-medium">{formatDate(group.date, today)}</h2>
            <span
              className={cn(
                "text-sm tabular-nums",
                group.net > 0 && "text-money-in",
                group.net < 0 && "text-money-out",
                group.net === 0 && "text-muted-foreground",
              )}
            >
              {group.net > 0 ? "+" : group.net < 0 ? "−" : ""}
              {formatINR(Math.abs(group.net))}
            </span>
          </header>

          <ul className="divide-y rounded-xl border bg-surface">
            {group.rows.map((row) => {
              const category = row.categoryId ? categoryById.get(row.categoryId) : undefined;
              const parent = category?.parent_id ? categoryById.get(category.parent_id) : undefined;
              const categoryLabel = category
                ? parent
                  ? `${parent.name} › ${category.name}`
                  : category.name
                : undefined;
              const Icon = row.kind === "transfer" ? ArrowRightLeft : categoryIcon(category, parent);

              const from = row.fromAccountId ? accountName.get(row.fromAccountId) : undefined;
              const to = row.toAccountId ? accountName.get(row.toAccountId) : undefined;
              const party = row.partyId ? partyName.get(row.partyId) : undefined;
              // Note, else party name, else category (ui-context "Lists").
              const title =
                row.note ??
                party ??
                (row.kind === "transfer" ? "Transfer" : (categoryLabel ?? "Uncategorized"));
              const details = [
                row.kind === "transfer"
                  ? from && to
                    ? `${from} → ${to}`
                    : from
                      ? `From ${from}`
                      : to
                        ? `To ${to}`
                        : undefined
                  : title !== categoryLabel
                    ? categoryLabel
                    : undefined,
                row.note ? party : undefined,
                row.projectId ? projectName.get(row.projectId) : undefined,
                row.kind === "transfer" ? undefined : accountName.get(row.accountId ?? ""),
                ...row.tags.map((t) => `#${t}`),
              ].filter(Boolean);

              return (
                <li key={`${row.kind}-${row.id}`} className="flex items-center">
                  <button
                    type="button"
                    onClick={() => openEdit(row)}
                    className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-l-xl px-3 py-2 text-left outline-none hover:bg-subtle focus-visible:ring-3 focus-visible:ring-ring/50"
                  >
                    <Icon className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{title}</span>
                      {details.length > 0 && (
                        <span className="block truncate text-xs text-muted-foreground">
                          {details.join(" · ")}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 text-sm font-medium tabular-nums">
                      <Amount row={row} />
                    </span>
                  </button>

                  <DropdownMenu>
                    <DropdownMenuTrigger
                      render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}
                    >
                      <MoreVertical className="h-4 w-4" />
                      <span className="sr-only">Actions for {title}</span>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem className="min-h-11" onClick={() => openEdit(row)}>
                        <Pencil className="h-4 w-4" />
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        variant="destructive"
                        className="min-h-11"
                        onClick={() => remove(row)}
                      >
                        <Trash2 className="h-4 w-4" />
                        Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </li>
              );
            })}
          </ul>
        </section>
      ))}

      {query.hasNextPage && (
        <div>
          <Button
            variant="outline"
            className="h-11"
            disabled={query.isFetchingNextPage}
            onClick={() => query.fetchNextPage()}
          >
            {query.isFetchingNextPage ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
