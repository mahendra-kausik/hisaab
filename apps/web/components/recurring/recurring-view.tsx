"use client";

import { useState } from "react";
import { MoreVertical, Pause, Pencil, Play, Plus, Trash2 } from "lucide-react";
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
import { formatDate, todayIn } from "@/lib/dates";
import { formatINR } from "@/lib/format";
import { useAccounts } from "@/lib/queries/accounts";
import { useCategories } from "@/lib/queries/categories";
import { useMe } from "@/lib/queries/me";
import {
  useDeleteRule,
  usePauseResume,
  useRecurring,
  type RecurringRule,
} from "@/lib/queries/recurring";
import { frequencyLabel } from "@/lib/recurring";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { RuleDialog } from "./rule-dialog";

function RuleRow({
  rule,
  today,
  title,
  onEdit,
}: {
  rule: RecurringRule;
  today: string;
  title: string;
  onEdit: () => void;
}) {
  const { active } = useWorkspace();
  const pauseResume = usePauseResume(active.id);
  const remove = useDeleteRule(active.id);
  const income = rule.type === "income";

  function toggle() {
    const action = rule.paused ? "resume" : "pause";
    pauseResume.mutate(
      { id: rule.id, action },
      {
        onSuccess: () => toast(rule.paused ? "Resumed" : "Paused"),
        onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not update the entry"),
      },
    );
  }

  function onDelete() {
    remove.mutate(rule.id, {
      onSuccess: () => toast("Recurring entry deleted. Entries already added stay."),
      onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not delete the entry"),
    });
  }

  const next = rule.upcoming[0];
  return (
    <li className="flex items-center">
      <div className="flex min-h-14 min-w-0 flex-1 items-center gap-3 px-3 py-2">
        <span className="min-w-0 flex-1">
          <span className={cn("block truncate text-sm font-medium", rule.paused && "text-muted-foreground")}>
            {title}
            {rule.paused && (
              <span className="ml-2 rounded-full bg-subtle px-2 py-0.5 text-xs font-normal">Paused</span>
            )}
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {frequencyLabel(rule.frequency, rule.interval_count)}
            {" · "}
            {next ? `Next ${formatDate(next, today)}` : "Ended"}
            {rule.upcoming.length > 1 &&
              `, then ${rule.upcoming
                .slice(1)
                .map((d) => formatDate(d, today))
                .join(", ")}`}
          </span>
        </span>
        <span className={cn("shrink-0 text-sm font-medium tabular-nums", income ? "text-money-in" : "text-money-out")}>
          {income ? "+" : "−"}
          {formatINR(rule.amount_paise)}
        </span>
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}>
          <MoreVertical className="h-4 w-4" />
          <span className="sr-only">Actions for {title}</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem className="min-h-11" onClick={onEdit}>
            <Pencil className="h-4 w-4" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuItem className="min-h-11" onClick={toggle}>
            {rule.paused ? <Play className="h-4 w-4" /> : <Pause className="h-4 w-4" />}
            {rule.paused ? "Resume" : "Pause"}
          </DropdownMenuItem>
          <DropdownMenuItem className="min-h-11" onClick={onDelete}>
            <Trash2 className="h-4 w-4" />
            Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  );
}

export function RecurringView() {
  const { active } = useWorkspace();
  const me = useMe();
  const query = useRecurring(active.id);
  const categories = useCategories(active.id);
  const accounts = useAccounts(active.id);
  // null = closed; { rule: null } = adding; { rule } = editing that one.
  const [dialog, setDialog] = useState<{ rule: RecurringRule | null } | null>(null);

  const today = todayIn(me.data?.timezone ?? "Asia/Kolkata");
  // Row title: the note, else the category, else the account.
  const title = (r: RecurringRule) =>
    r.note ||
    categories.data?.find((c) => c.id === r.category_id)?.name ||
    accounts.data?.find((a) => a.id === r.account_id)?.name ||
    (r.type === "income" ? "Income" : "Expense");
  const rules = query.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Recurring</h1>
        <Button className="h-11 gap-2" onClick={() => setDialog({ rule: null })}>
          <Plus className="h-4 w-4" />
          Add recurring
        </Button>
      </div>

      {query.isError ? (
        <div className="flex flex-col items-start gap-3 py-6">
          <p>We could not load your recurring entries.</p>
          <Button className="h-11" onClick={() => query.refetch()}>
            Try again
          </Button>
        </div>
      ) : query.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : rules.length === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-xl border bg-surface p-6">
          <p>No recurring entries — Add rent, a subscription or a salary and it is recorded for you.</p>
          <Button className="h-11" onClick={() => setDialog({ rule: null })}>
            Add recurring
          </Button>
        </div>
      ) : (
        <ul className="divide-y rounded-xl border bg-surface">
          {rules.map((r) => (
            <RuleRow
              key={r.id}
              rule={r}
              today={today}
              title={title(r)}
              onEdit={() => setDialog({ rule: r })}
            />
          ))}
        </ul>
      )}

      {dialog && <RuleDialog editing={dialog.rule} onClose={() => setDialog(null)} />}
    </div>
  );
}
