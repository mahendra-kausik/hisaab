"use client";

import { useState } from "react";
import Link from "next/link";
import { Archive, ArchiveRestore, MoreVertical, Pencil, Plus } from "lucide-react";
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
import { formatINR } from "@/lib/format";
import { useAccounts, useUpdateAccount, type Account } from "@/lib/queries/accounts";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { AccountDialog } from "./account-dialog";
import { accountTypeInfo } from "./account-types";

/** The balance with its sign; a negative one (money owed) also shows the "−" and the money-out colour. */
function Balance({ paise }: { paise: number }) {
  return (
    <span className={cn("tabular-nums", paise < 0 && "text-money-out", paise === 0 && "text-muted-foreground")}>
      {paise < 0 ? "−" : ""}
      {formatINR(Math.abs(paise))}
    </span>
  );
}

function AccountRow({ account, onEdit }: { account: Account; onEdit: () => void }) {
  const { active } = useWorkspace();
  const update = useUpdateAccount(active.id);
  const { label, icon: Icon } = accountTypeInfo(account.type);
  const archived = !!account.archived_at;

  function setArchived(value: boolean) {
    update.mutate(
      { id: account.id, body: { archived: value } },
      {
        onSuccess: () => toast(value ? `${account.name} archived` : `${account.name} restored`),
        onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not update the account"),
      },
    );
  }

  return (
    <li className="flex items-center">
      {/* Opens the Transactions page filtered to this account. */}
      <Link
        href={`/transactions?account_id=${account.id}`}
        className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-l-xl px-3 py-2 outline-none hover:bg-subtle focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        <Icon className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className={cn("block truncate text-sm font-medium", archived && "text-muted-foreground")}>
            {account.name}
          </span>
          <span className="block truncate text-xs text-muted-foreground">{label}</span>
        </span>
        <span className="shrink-0 text-sm font-medium">
          <Balance paise={account.balance_paise} />
        </span>
      </Link>

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}>
          <MoreVertical className="h-4 w-4" />
          <span className="sr-only">Actions for {account.name}</span>
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

export function AccountsView() {
  const { active } = useWorkspace();
  const query = useAccounts(active.id);
  // null = closed; { account: null } = adding; { account } = editing that one.
  const [dialog, setDialog] = useState<{ account: Account | null } | null>(null);

  const accounts = query.data ?? [];
  const open = accounts.filter((a) => !a.archived_at);
  const archived = accounts.filter((a) => a.archived_at);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Accounts</h1>
        <Button className="h-11 gap-2" onClick={() => setDialog({ account: null })}>
          <Plus className="h-4 w-4" />
          Add account
        </Button>
      </div>

      {query.isError ? (
        <div className="flex flex-col items-start gap-3 py-6">
          <p>We could not load your accounts.</p>
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
              <p>No open accounts — Add an account to start recording money.</p>
              <Button className="h-11" onClick={() => setDialog({ account: null })}>
                Add account
              </Button>
            </div>
          ) : (
            <ul className="divide-y rounded-xl border bg-surface">
              {open.map((a) => (
                <AccountRow key={a.id} account={a} onEdit={() => setDialog({ account: a })} />
              ))}
            </ul>
          )}

          {archived.length > 0 && (
            <section aria-labelledby="archived-heading" className="flex flex-col gap-2">
              <h2 id="archived-heading" className="px-1 text-sm font-medium text-muted-foreground">
                Archived
              </h2>
              <ul className="divide-y rounded-xl border bg-surface">
                {archived.map((a) => (
                  <AccountRow key={a.id} account={a} onEdit={() => setDialog({ account: a })} />
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      {dialog && <AccountDialog editing={dialog.account} onClose={() => setDialog(null)} />}
    </div>
  );
}
