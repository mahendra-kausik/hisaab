"use client";

import Link from "next/link";
import { Ban, HandCoins, MoreVertical, Pencil, Undo2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ApiError } from "@/lib/api/client";
import { dueWhen, STATE_LABEL } from "@/lib/dues";
import { formatINR } from "@/lib/format";
import { useCancelReopenDue, type Due } from "@/lib/queries/dues";
import { useWorkspace } from "@/lib/workspace";
import { cn } from "@/lib/utils";

/**
 * One due. The state is always a text badge (never colour alone); the amount carries a label too:
 * "They owe you" is money in, "You owe" is money out.
 */
export function DueRow({
  due,
  partyName,
  today,
  showParty = true,
  onEdit,
  onPay,
}: {
  due: Due;
  partyName: string;
  today: string;
  showParty?: boolean;
  onEdit: () => void;
  onPay: () => void;
}) {
  const { active } = useWorkspace();
  const cancelReopen = useCancelReopenDue(active.id);
  const cancelled = due.state === "cancelled";
  const settled = due.state === "settled";
  const receivable = due.direction === "receivable";

  function setCancelled(value: boolean) {
    cancelReopen.mutate(
      { id: due.id, action: value ? "cancel" : "reopen" },
      {
        onSuccess: () => toast(value ? "Due cancelled" : "Due reopened"),
        onError: (e) => toast.error(e instanceof ApiError ? e.message : "Could not update the due"),
      },
    );
  }

  return (
    <li className="flex items-center">
      <div className="flex min-h-16 min-w-0 flex-1 flex-col gap-0.5 px-3 py-2">
        <div className="flex items-center gap-2">
          {showParty ? (
            <Link
              href={`/parties/${due.party_id}`}
              className="min-w-0 truncate rounded-md text-sm font-medium outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              {partyName}
            </Link>
          ) : (
            <span className="min-w-0 truncate text-sm font-medium">{due.reason}</span>
          )}
          <span
            className={cn(
              "shrink-0 rounded-md bg-subtle px-1.5 py-0.5 text-xs",
              due.state === "overdue" && "text-state-error",
            )}
          >
            {STATE_LABEL[due.state]}
          </span>
        </div>
        <p className="truncate text-xs text-muted-foreground">
          {[showParty ? due.reason : null, cancelled || settled ? null : dueWhen(due.due_date, today)]
            .filter(Boolean)
            .join(" · ")}
        </p>
      </div>

      <div
        className={cn(
          "shrink-0 px-2 text-right tabular-nums",
          cancelled && "text-muted-foreground line-through",
        )}
      >
        <p className={cn("text-sm font-medium", !cancelled && (receivable ? "text-money-in" : "text-money-out"))}>
          {receivable ? "They owe you" : "You owe"} {formatINR(due.outstanding_paise)}
        </p>
        {due.outstanding_paise !== due.amount_paise && (
          <p className="text-xs text-muted-foreground">of {formatINR(due.amount_paise)}</p>
        )}
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" className="size-11 shrink-0" />}>
          <MoreVertical className="h-4 w-4" />
          <span className="sr-only">Actions for {due.reason}</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {!cancelled && !settled && (
            <DropdownMenuItem className="min-h-11" onClick={onPay}>
              <HandCoins className="h-4 w-4" />
              Record payment
            </DropdownMenuItem>
          )}
          <DropdownMenuItem className="min-h-11" onClick={onEdit}>
            <Pencil className="h-4 w-4" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuItem className="min-h-11" onClick={() => setCancelled(!cancelled)}>
            {cancelled ? <Undo2 className="h-4 w-4" /> : <Ban className="h-4 w-4" />}
            {cancelled ? "Reopen" : "Cancel due"}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  );
}
