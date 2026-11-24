"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Download, Plus } from "lucide-react";
import { DueDialog } from "@/components/dues/due-dialog";
import { DueRow } from "@/components/dues/due-row";
import { PaymentDialog } from "@/components/dues/payment-dialog";
import { TransactionList } from "@/components/transactions/transaction-list";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { todayIn } from "@/lib/dates";
import { partyNet } from "@/lib/dues";
import { formatINR } from "@/lib/format";
import { useDues, usePartyTotals, type Due } from "@/lib/queries/dues";
import { useMe } from "@/lib/queries/me";
import { PARTY_ROLES, useParties } from "@/lib/queries/parties";
import { useWorkspace } from "@/lib/workspace";

function Tile({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border bg-surface p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{children}</p>
    </div>
  );
}

/** One party: what was paid and received, what is still owed, their dues and their transactions. */
export function PartyLedger() {
  const { partyId } = useParams<{ partyId: string }>();
  const { active } = useWorkspace();
  const me = useMe();
  const parties = useParties(active.id);
  const dues = useDues(active.id, partyId);
  const totals = usePartyTotals(active.id, partyId);
  const [dialog, setDialog] = useState<{ due: Due | null } | null>(null);
  const [paying, setPaying] = useState<Due | null>(null);
  const today = todayIn(me.data?.timezone ?? "Asia/Kolkata");

  const back = (
    <Link
      href="/parties"
      className="inline-flex min-h-11 items-center gap-2 rounded-md text-sm text-muted-foreground outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <ArrowLeft className="h-4 w-4" /> Parties
    </Link>
  );

  if (parties.isPending) return <Skeleton className="h-40 w-full" aria-busy="true" />;
  const party = parties.data?.find((p) => p.id === partyId);
  if (!party) {
    return (
      <div className="flex flex-col items-start gap-3">
        {back}
        <p>Party not found.</p>
      </div>
    );
  }

  const net = partyNet(dues.data ?? []);
  const role = PARTY_ROLES.find((r) => r.value === party.role)?.label ?? party.role;
  // Open dues first, cancelled last; the API's order is kept inside each group.
  const sorted = [...(dues.data ?? [])].sort(
    (a, b) => Number(a.state === "cancelled") - Number(b.state === "cancelled"),
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        {back}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <h1 className="truncate text-2xl font-semibold">{party.name}</h1>
            <p className="truncate text-sm text-muted-foreground">
              {[role, party.phone, party.archived_at ? "Archived" : null].filter(Boolean).join(" · ")}
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            {/* A plain link: the browser downloads the PDF with the session cookie (same as the
                CSV export on Transactions). All-time statement; the API names the file. */}
            <a
              href={`/api/v1/workspaces/${active.id}/parties/${partyId}/statement.pdf`}
              download
              className={`${buttonVariants({ variant: "outline" })} h-11 gap-2`}
            >
              <Download className="h-4 w-4" />
              Download statement
            </a>
            <Button className="h-11 gap-2" onClick={() => setDialog({ due: null })}>
              <Plus className="h-4 w-4" />
              Add due
            </Button>
          </div>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Tile label="Total paid">{totals.data ? formatINR(totals.data.paid) : "—"}</Tile>
        <Tile label="Total received">{totals.data ? formatINR(totals.data.received) : "—"}</Tile>
        <Tile label="Net balance">
          {dues.data ? (
            <>
              {formatINR(Math.abs(net))}
              <span className="block text-sm font-normal text-muted-foreground">
                {net > 0 ? "They owe you" : net < 0 ? "You owe them" : "Settled up"}
              </span>
            </>
          ) : (
            "—"
          )}
        </Tile>
      </div>

      <section aria-labelledby="ledger-dues" className="flex flex-col gap-2">
        <h2 id="ledger-dues" className="px-1 text-sm font-medium text-muted-foreground">
          Dues
        </h2>
        {dues.isError ? (
          <p>We could not load the dues.</p>
        ) : dues.isPending ? (
          <Skeleton className="h-16 w-full" />
        ) : sorted.length === 0 ? (
          <p className="rounded-xl border bg-surface p-4 text-sm">No dues with {party.name}.</p>
        ) : (
          <ul className="divide-y rounded-xl border bg-surface">
            {sorted.map((d) => (
              <DueRow
                key={d.id}
                due={d}
                partyName={party.name}
                today={today}
                showParty={false}
                onEdit={() => setDialog({ due: d })}
                onPay={() => setPaying(d)}
              />
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="ledger-transactions" className="flex flex-col gap-2">
        <h2 id="ledger-transactions" className="px-1 text-sm font-medium text-muted-foreground">
          Transactions
        </h2>
        <TransactionList filters={{ party_id: partyId }} onClear={() => {}} />
      </section>

      {dialog && (
        <DueDialog editing={dialog.due} presetPartyId={partyId} onClose={() => setDialog(null)} />
      )}
      {paying && <PaymentDialog due={paying} onClose={() => setPaying(null)} />}
    </div>
  );
}
