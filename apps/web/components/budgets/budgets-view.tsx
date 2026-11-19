"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { budgetState, periodLabel, shiftDay } from "@/lib/budgets";
import { todayIn } from "@/lib/dates";
import { formatINR, paiseToRupeesInput, rupeesToPaise } from "@/lib/format";
import { useBudgets, useSetBudget, type BudgetItem } from "@/lib/queries/budgets";
import { useCategories } from "@/lib/queries/categories";
import { useMe } from "@/lib/queries/me";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";

const BAR = { ok: "bg-primary", warning: "bg-amber-500", over: "bg-destructive" } as const;

/** Amount field for one category. Mount it only while editing, so it starts fresh. */
function AmountForm({
  categoryId,
  initialPaise,
  onDone,
}: {
  categoryId: string;
  initialPaise?: number;
  onDone: () => void;
}) {
  const { active } = useWorkspace();
  const setBudget = useSetBudget(active.id);
  const [error, setError] = useState<string | null>(null);

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = String(new FormData(event.currentTarget).get("amount") ?? "").trim();
    // Empty means "remove the budget".
    const paise = text === "" ? 0 : rupeesToPaise(text);
    if (paise === null) return setError("Enter an amount like 5000 or 1500.50");
    setBudget.mutate(
      { categoryId, amountPaise: paise },
      {
        onSuccess: () => {
          toast(paise === 0 ? "Budget removed" : "Budget saved");
          onDone();
        },
        onError: (e) => setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
      },
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <Input
          name="amount"
          inputMode="decimal"
          autoFocus
          aria-label="Monthly budget in rupees"
          placeholder="₹ per month"
          defaultValue={initialPaise ? paiseToRupeesInput(initialPaise) : ""}
          className="h-11 w-36"
        />
        <Button type="submit" className="h-11" disabled={setBudget.isPending}>
          Save
        </Button>
        <Button type="button" variant="ghost" className="h-11" onClick={onDone}>
          Cancel
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </form>
  );
}

function BudgetRow({ item, editable }: { item: BudgetItem; editable: boolean }) {
  const [editing, setEditing] = useState(false);
  const state = budgetState(item.spent_paise, item.amount_paise);
  const pct = Math.min(100, Math.floor((item.spent_paise * 100) / item.amount_paise));
  const left = item.amount_paise - item.spent_paise;

  return (
    <li className="flex flex-col gap-2 p-4">
      <div className="flex items-center justify-between gap-3">
        <span className="min-w-0 truncate text-sm font-medium">{item.category_name}</span>
        {editable && !editing && (
          <Button variant="ghost" className="h-11" onClick={() => setEditing(true)}>
            Edit
          </Button>
        )}
      </div>
      {editing ? (
        <AmountForm
          categoryId={item.category_id}
          initialPaise={item.amount_paise}
          onDone={() => setEditing(false)}
        />
      ) : (
        <>
          <div
            role="progressbar"
            aria-label={`${item.category_name} budget used`}
            aria-valuenow={pct}
            aria-valuemin={0}
            aria-valuemax={100}
            className="h-2 overflow-hidden rounded-full bg-subtle"
          >
            <div className={cn("h-full rounded-full", BAR[state])} style={{ width: `${pct}%` }} />
          </div>
          <div className="flex justify-between gap-3 text-sm">
            <span className="text-muted-foreground">
              {formatINR(item.spent_paise)} of {formatINR(item.amount_paise)}
            </span>
            <span
              className={cn(
                state === "over" && "font-medium text-destructive",
                state === "warning" && "font-medium text-amber-600",
                state === "ok" && "text-muted-foreground",
              )}
            >
              {state === "over" ? `Over by ${formatINR(-left)}` : `${formatINR(left)} left`}
            </span>
          </div>
        </>
      )}
    </li>
  );
}

function UnbudgetedRow({ id, name }: { id: string; name: string }) {
  const [editing, setEditing] = useState(false);
  return (
    <li className="flex flex-col gap-2 p-4">
      <div className="flex items-center justify-between gap-3">
        <span className="min-w-0 truncate text-sm font-medium">{name}</span>
        {!editing && (
          <Button variant="outline" className="h-11" onClick={() => setEditing(true)}>
            Set budget
          </Button>
        )}
      </div>
      {editing && <AmountForm categoryId={id} onDone={() => setEditing(false)} />}
    </li>
  );
}

export function BudgetsView() {
  const { active } = useWorkspace();
  const router = useRouter();
  const pathname = usePathname();
  const day = useSearchParams().get("period") ?? undefined;
  const me = useMe();
  const budgets = useBudgets(active.id, day);
  const categories = useCategories(active.id);

  function go(target: string | undefined) {
    router.replace(target ? `${pathname}?period=${target}` : pathname);
  }

  if (budgets.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        Could not load budgets.
      </p>
    );
  }
  if (!budgets.data || !categories.data || !me.data) {
    return <Skeleton className="h-64 w-full rounded-xl" />;
  }

  const { period_start: start, period_end: end, items } = budgets.data;
  const today = todayIn(me.data.timezone);
  const isCurrent = start <= today && today <= end;
  const budgeted = new Set(items.map((i) => i.category_id));
  // Only visible top-level expense categories can have a budget.
  const unbudgeted = categories.data.filter(
    (c) => c.kind === "expense" && !c.parent_id && !c.hidden_at && !budgeted.has(c.id),
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Budgets</h1>
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon" className="size-11" onClick={() => go(shiftDay(start, -1))}>
            <ChevronLeft className="h-4 w-4" />
            <span className="sr-only">Previous period</span>
          </Button>
          <span className="min-w-44 text-center text-sm font-medium">{periodLabel(start, end)}</span>
          <Button
            variant="outline"
            size="icon"
            className="size-11"
            disabled={isCurrent}
            onClick={() => go(shiftDay(end, 1))}
          >
            <ChevronRight className="h-4 w-4" />
            <span className="sr-only">Next period</span>
          </Button>
        </div>
      </div>

      {!isCurrent && (
        <p className="text-sm text-muted-foreground">
          Past periods are read-only.{" "}
          <button className="underline" onClick={() => go(undefined)}>
            Back to this period
          </button>
        </p>
      )}

      {items.length > 0 && (
        <ul className="divide-y rounded-xl border bg-surface">
          {items.map((item) => (
            <BudgetRow key={item.category_id} item={item} editable={isCurrent} />
          ))}
        </ul>
      )}
      {items.length === 0 && (
        <p className="text-sm text-muted-foreground">No budgets set for this period.</p>
      )}

      {isCurrent && unbudgeted.length > 0 && (
        <section aria-labelledby="unbudgeted-heading" className="flex flex-col gap-2">
          <h2 id="unbudgeted-heading" className="px-1 text-lg font-semibold">
            No budget yet
          </h2>
          <ul className="divide-y rounded-xl border bg-surface">
            {unbudgeted.map((c) => (
              <UnbudgetedRow key={c.id} id={c.id} name={c.name} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
