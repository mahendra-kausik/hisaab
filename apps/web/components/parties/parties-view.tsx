"use client";

import { useState } from "react";
import Link from "next/link";
import { Archive, ArchiveRestore, MoreVertical, Pencil, Plus, UserRound } from "lucide-react";
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
import { PARTY_ROLES, useParties, useUpdateParty, type Party } from "@/lib/queries/parties";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { PartyDialog } from "./party-dialog";

const roleLabel = (role: Party["role"]) => PARTY_ROLES.find((r) => r.value === role)?.label ?? role;

function PartyRow({ party, onEdit }: { party: Party; onEdit: () => void }) {
  const { active } = useWorkspace();
  const update = useUpdateParty(active.id);
  const archived = !!party.archived_at;

  function setArchived(value: boolean) {
    update.mutate(
      { id: party.id, body: { archived: value } },
      {
        onSuccess: () => toast(value ? `${party.name} archived` : `${party.name} restored`),
        onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not update the party"),
      },
    );
  }

  return (
    <li className="flex items-center">
      {/* Opens this party's ledger: totals, dues and transactions. */}
      <Link
        href={`/parties/${party.id}`}
        className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-l-xl px-3 py-2 outline-none hover:bg-subtle focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <UserRound className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className={cn("block truncate text-sm font-medium", archived && "text-muted-foreground")}>
            {party.name}
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {[roleLabel(party.role), party.phone].filter(Boolean).join(" · ")}
          </span>
        </span>
      </Link>

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}>
          <MoreVertical className="h-4 w-4" />
          <span className="sr-only">Actions for {party.name}</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem className="min-h-11" onClick={onEdit}>
            <Pencil className="h-4 w-4" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuItem className="min-h-11" onClick={() => setArchived(!archived)}>
            {archived ? <ArchiveRestore className="h-4 w-4" /> : <Archive className="h-4 w-4" />}
            {archived ? "Unarchive" : "Archive"}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  );
}

export function PartiesView() {
  const { active } = useWorkspace();
  const query = useParties(active.id);
  // null = closed; { party: null } = adding; { party } = editing that one.
  const [dialog, setDialog] = useState<{ party: Party | null } | null>(null);

  const parties = query.data ?? [];
  const open = parties.filter((p) => !p.archived_at);
  const archived = parties.filter((p) => p.archived_at);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Parties</h1>
        <Button className="h-11 gap-2" onClick={() => setDialog({ party: null })}>
          <Plus className="h-4 w-4" />
          Add party
        </Button>
      </div>

      {query.isError ? (
        <div className="flex flex-col items-start gap-3 py-6">
          <p>We could not load your parties.</p>
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
      ) : (
        <>
          {open.length === 0 ? (
            <div className="flex flex-col items-start gap-3 rounded-xl border bg-surface p-6">
              <p>No parties yet — Add the people you pay or get money from.</p>
              <Button className="h-11" onClick={() => setDialog({ party: null })}>
                Add party
              </Button>
            </div>
          ) : (
            <ul className="divide-y rounded-xl border bg-surface">
              {open.map((p) => (
                <PartyRow key={p.id} party={p} onEdit={() => setDialog({ party: p })} />
              ))}
            </ul>
          )}

          {archived.length > 0 && (
            <section aria-labelledby="archived-heading" className="flex flex-col gap-2">
              <h2 id="archived-heading" className="px-1 text-sm font-medium text-muted-foreground">
                Archived
              </h2>
              <ul className="divide-y rounded-xl border bg-surface">
                {archived.map((p) => (
                  <PartyRow key={p.id} party={p} onEdit={() => setDialog({ party: p })} />
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      {dialog && <PartyDialog editing={dialog.party} onClose={() => setDialog(null)} />}
    </div>
  );
}
