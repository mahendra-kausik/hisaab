"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { dueSection, DUE_SECTIONS } from "@/lib/dues";
import { todayIn } from "@/lib/dates";
import { useDues, type Due } from "@/lib/queries/dues";
import { useMe } from "@/lib/queries/me";
import { useParties } from "@/lib/queries/parties";
import { useWorkspace } from "@/lib/workspace";
import { DueDialog } from "./due-dialog";
import { DueRow } from "./due-row";
import { PaymentDialog } from "./payment-dialog";

/** The Dues page: overdue, due today, upcoming (7 days), later, settled and cancelled. */
export function DuesView() {
  const { active } = useWorkspace();
  const me = useMe();
  const query = useDues(active.id);
  const parties = useParties(active.id);
  const today = todayIn(me.data?.timezone ?? "Asia/Kolkata");
  // null = closed; { due: null } = adding; { due } = editing that one.
  const [dialog, setDialog] = useState<{ due: Due | null } | null>(null);
  const [paying, setPaying] = useState<Due | null>(null);

  const partyName = new Map((parties.data ?? []).map((p) => [p.id, p.name]));
  const dues = query.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Dues</h1>
        <Button className="h-11 gap-2" onClick={() => setDialog({ due: null })}>
          <Plus className="h-4 w-4" />
          Add due
        </Button>
      </div>

      {query.isError ? (
        <div className="flex flex-col items-start gap-3 py-6">
          <p>We could not load your dues.</p>
          <Button className="h-11" onClick={() => query.refetch()}>
            Try again
          </Button>
        </div>
      ) : query.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : dues.length === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-xl border bg-surface p-6">
          <p>No dues yet — Add money someone owes you, or money you owe.</p>
          <Button className="h-11" onClick={() => setDialog({ due: null })}>
            Add due
          </Button>
        </div>
      ) : (
        DUE_SECTIONS.map(({ key, label }) => {
          const rows = dues.filter((d) => dueSection(d, today) === key);
          if (rows.length === 0) return null;
          return (
            <section key={key} aria-labelledby={`dues-${key}`} className="flex flex-col gap-2">
              <h2 id={`dues-${key}`} className="px-1 text-sm font-medium text-muted-foreground">
                {label} ({rows.length})
              </h2>
              <ul className="divide-y rounded-xl border bg-surface">
                {rows.map((d) => (
                  <DueRow
                    key={d.id}
                    due={d}
                    partyName={partyName.get(d.party_id) ?? "Unknown party"}
                    today={today}
                    onEdit={() => setDialog({ due: d })}
                    onPay={() => setPaying(d)}
                  />
                ))}
              </ul>
            </section>
          );
        })
      )}

      {dialog && <DueDialog editing={dialog.due} onClose={() => setDialog(null)} />}
      {paying && <PaymentDialog due={paying} onClose={() => setPaying(null)} />}
    </div>
  );
}
