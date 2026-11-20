"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { NativeSelect } from "@/components/transactions/native-select";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { todayIn } from "@/lib/dates";
import { formatINR, paiseToRupeesInput, rupeesToPaise } from "@/lib/format";
import { useAccounts } from "@/lib/queries/accounts";
import type { Due } from "@/lib/queries/dues";
import { useMe } from "@/lib/queries/me";
import { useSaveTransaction } from "@/lib/queries/transactions";
import { useWorkspace } from "@/lib/workspace";

const schema = z.object({
  amountPaise: z
    .number({ error: "Enter an amount like 250 or 99.50" })
    .int()
    .positive("The amount must be greater than zero"),
  accountId: z.string().min(1, "Choose an account"),
  occurredOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Pick a date"),
  note: z.string().trim().max(500, "Use at most 500 characters"),
});

/**
 * Record a (part) payment against a due. A payment is an ordinary transaction sent with `due_id`:
 * income when someone pays me back (receivable), an expense when I pay (payable). The API forces
 * the due's party and rejects more than is outstanding (422 `overpayment`), shown under the form.
 */
export function PaymentDialog({ due, onClose }: { due: Due; onClose: () => void }) {
  const { active } = useWorkspace();
  const me = useMe();
  const accounts = useAccounts(active.id);
  const save = useSaveTransaction(active.id);

  const receivable = due.direction === "receivable";
  const [amount, setAmount] = useState(paiseToRupeesInput(due.outstanding_paise));
  const [accountId, setAccountId] = useState("");
  const [date, setDate] = useState(todayIn(me.data?.timezone ?? "Asia/Kolkata"));
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const openAccounts = (accounts.data ?? []).filter((a) => !a.archived_at);
  const chosenAccount = accountId || openAccounts[0]?.id || "";

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const parsed = schema.safeParse({
      amountPaise: rupeesToPaise(amount) ?? undefined,
      accountId: chosenAccount,
      occurredOn: date,
      note,
    });
    if (!parsed.success) return setError(parsed.error.issues[0].message);
    const v = parsed.data;

    save.mutate(
      {
        body: {
          account_id: v.accountId,
          type: receivable ? "income" : "expense",
          amount_paise: v.amountPaise,
          occurred_on: v.occurredOn,
          due_id: due.id,
          category_id: due.category_id,
          project_id: due.project_id,
          note: v.note || undefined,
          tags: [],
        },
      },
      {
        onSuccess: () => {
          toast("Payment recorded");
          onClose();
        },
        onError: (e) =>
          setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
      },
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-2xl sm:max-w-md">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{receivable ? "Record payment received" : "Record payment made"}</DialogTitle>
            <DialogDescription>
              {formatINR(due.outstanding_paise)} is still outstanding on “{due.reason}”.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pay-amount">Amount (₹)</Label>
            <Input
              id="pay-amount"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              inputMode="decimal"
              autoComplete="off"
              autoFocus
              className="h-12 text-lg tabular-nums"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pay-account">{receivable ? "Received into" : "Paid from"}</Label>
            <NativeSelect
              id="pay-account"
              value={chosenAccount}
              onChange={(e) => setAccountId(e.target.value)}
            >
              {openAccounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </NativeSelect>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pay-date">Date</Label>
            <Input
              id="pay-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="h-11"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pay-note">Note (optional)</Label>
            <Input
              id="pay-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              autoComplete="off"
              className="h-11"
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" className="h-11" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" className="h-11" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
