"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { AttachmentList } from "@/components/attachments/attachment-list";
import { ProjectSelect } from "@/components/projects/project-select";
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
import { paiseToRupeesInput, rupeesToPaise } from "@/lib/format";
import { useAccounts } from "@/lib/queries/accounts";
import { useCategories } from "@/lib/queries/categories";
import { useSaveDue, type Due } from "@/lib/queries/dues";
import { useMe } from "@/lib/queries/me";
import { useParties } from "@/lib/queries/parties";
import { categoryItems } from "@/lib/transactions";
import { useWorkspace } from "@/lib/workspace";

type Direction = Due["direction"];

const DIRECTIONS: { value: Direction; label: string }[] = [
  { value: "receivable", label: "They owe me" },
  { value: "payable", label: "I owe them" },
];

// The same limits as the API, so most mistakes are caught before a request is sent.
const schema = z.object({
  partyId: z.string().min(1, "Choose a party"),
  amountPaise: z
    .number({ error: "Enter an amount like 250 or 99.50" })
    .int()
    .positive("The amount must be greater than zero"),
  reason: z.string().trim().min(1, "Enter a reason").max(200, "Use at most 200 characters"),
  dueDate: z.string().regex(/^(\d{4}-\d{2}-\d{2})?$/, "Pick a date"),
});

const originSchema = z.object({
  accountId: z.string().min(1, "Choose an account"),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Pick a date for the money handed over"),
  note: z.string().trim().max(500, "Use at most 500 characters"),
});

/**
 * Add (`editing` is null) or edit a due. Mount it only while open so the form starts fresh.
 * `presetPartyId` fills the party when the dialog is opened from a party's ledger. When adding,
 * "record the money handed over" also creates the loan itself (an expense for a receivable, income
 * for a payable) in the same request; it is not a payment and does not reduce what is owed.
 */
export function DueDialog({
  editing,
  presetPartyId,
  onClose,
}: {
  editing: Due | null;
  presetPartyId?: string;
  onClose: () => void;
}) {
  const { active } = useWorkspace();
  const me = useMe();
  const parties = useParties(active.id);
  const accounts = useAccounts(active.id);
  const categories = useCategories(active.id);
  const save = useSaveDue(active.id);
  const today = todayIn(me.data?.timezone ?? "Asia/Kolkata");

  const [direction, setDirection] = useState<Direction>(editing?.direction ?? "receivable");
  const [partyId, setPartyId] = useState(editing?.party_id ?? presetPartyId ?? "");
  const [amount, setAmount] = useState(editing ? paiseToRupeesInput(editing.amount_paise) : "");
  const [reason, setReason] = useState(editing?.reason ?? "");
  const [dueDate, setDueDate] = useState(editing?.due_date ?? "");
  const [projectId, setProjectId] = useState(editing?.project_id ?? "");
  const [categoryId, setCategoryId] = useState(editing?.category_id ?? "");
  const [handed, setHanded] = useState(false);
  const [originAccountId, setOriginAccountId] = useState("");
  const [originDate, setOriginDate] = useState(today);
  const [originCategoryId, setOriginCategoryId] = useState("");
  const [originNote, setOriginNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const openAccounts = (accounts.data ?? []).filter((a) => !a.archived_at);
  const accountId = originAccountId || openAccounts[0]?.id || "";
  // Archived parties stay in the list only while they are the saved choice.
  const partyOptions = (parties.data ?? []).filter((p) => !p.archived_at || p.id === partyId);

  // Receivable: they owe me, so the due's category is income and the loan I hand over is an expense.
  const dueKind = direction === "receivable" ? "income" : "expense";
  const originKind = direction === "receivable" ? "expense" : "income";
  const dueCategories = categoryItems(categories.data ?? [], dueKind, { keepId: editing?.category_id });
  const originCategories = categoryItems(categories.data ?? [], originKind);

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const parsed = schema.safeParse({
      partyId,
      amountPaise: rupeesToPaise(amount) ?? undefined,
      reason,
      dueDate,
    });
    if (!parsed.success) return setError(parsed.error.issues[0].message);
    const v = parsed.data;

    const callbacks = {
      onSuccess: () => {
        toast(editing ? "Due updated" : "Due added");
        onClose();
      },
      onError: (e: Error) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    };

    if (editing) {
      // PUT replaces amount, reason, date, project and category; party and direction never change.
      save.mutate(
        {
          id: editing.id,
          body: {
            amount_paise: v.amountPaise,
            reason: v.reason,
            due_date: v.dueDate || undefined,
            project_id: projectId || undefined,
            category_id: categoryId || undefined,
          },
        },
        callbacks,
      );
      return;
    }

    let origin;
    if (handed) {
      const o = originSchema.safeParse({ accountId, date: originDate, note: originNote });
      if (!o.success) return setError(o.error.issues[0].message);
      origin = {
        account_id: o.data.accountId,
        occurred_on: o.data.date,
        category_id: originCategoryId || undefined,
        note: o.data.note || undefined,
      };
    }
    save.mutate(
      {
        body: {
          party_id: v.partyId,
          direction,
          amount_paise: v.amountPaise,
          reason: v.reason,
          due_date: v.dueDate || undefined,
          project_id: projectId || undefined,
          category_id: categoryId || undefined,
          origin,
        },
      },
      callbacks,
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-2xl sm:max-w-md">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit due" : "Add due"}</DialogTitle>
            <DialogDescription>
              Money someone owes you, or money you owe. Record part payments against it later.
            </DialogDescription>
          </DialogHeader>

          <fieldset disabled={!!editing} className="flex flex-col gap-1.5">
            <legend className="mb-1.5 text-sm font-medium">Who owes whom</legend>
            <div className="grid grid-cols-2 gap-1 rounded-md bg-subtle p-1">
              {DIRECTIONS.map((d) => (
                <label key={d.value} className="relative">
                  <input
                    type="radio"
                    name="direction"
                    value={d.value}
                    checked={direction === d.value}
                    onChange={() => {
                      setDirection(d.value);
                      setCategoryId(""); // the two directions use different category kinds
                      setOriginCategoryId("");
                    }}
                    className="peer sr-only"
                  />
                  <span className="flex min-h-11 cursor-pointer items-center justify-center rounded-md text-sm font-medium text-muted-foreground peer-checked:bg-surface peer-checked:text-foreground peer-checked:shadow-sm peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50 peer-disabled:cursor-default peer-disabled:opacity-60">
                    {d.label}
                  </span>
                </label>
              ))}
            </div>
            {editing && (
              <p className="text-xs text-muted-foreground">
                The party and direction cannot change. Cancel this due and add a new one instead.
              </p>
            )}
          </fieldset>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="due-party">Party</Label>
            <NativeSelect
              id="due-party"
              value={partyId}
              disabled={!!editing}
              onChange={(e) => setPartyId(e.target.value)}
            >
              <option value="">Choose a party…</option>
              {partyOptions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                  {p.archived_at ? " (archived)" : ""}
                </option>
              ))}
            </NativeSelect>
            {!parties.isPending && partyOptions.length === 0 && (
              <p className="text-xs text-muted-foreground">Add a party first, on the Parties page.</p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="due-amount">Amount (₹)</Label>
            <Input
              id="due-amount"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              inputMode="decimal"
              autoComplete="off"
              placeholder="0"
              className="h-12 text-lg tabular-nums"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="due-reason">Reason</Label>
            <Input
              id="due-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              autoComplete="off"
              placeholder="Loan, advance, plot balance…"
              className="h-11"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="due-date">Due date (optional)</Label>
            <Input
              id="due-date"
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              className="h-11"
            />
          </div>

          <ProjectSelect
            id="due-project"
            value={projectId}
            onChange={setProjectId}
            keepId={editing?.project_id}
          />

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="due-category">
              Category for {direction === "receivable" ? "payments received" : "payments made"} (optional)
            </Label>
            <NativeSelect
              id="due-category"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <option value="">Uncategorized</option>
              {dueCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.label}
                </option>
              ))}
            </NativeSelect>
          </div>

          {!editing && (
            <div className="flex flex-col gap-4 rounded-xl border p-3">
              <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm font-medium">
                <input
                  type="checkbox"
                  checked={handed}
                  onChange={(e) => setHanded(e.target.checked)}
                  className="size-5 accent-primary"
                />
                {direction === "receivable"
                  ? "Record the money I handed over now"
                  : "Record the money I received now"}
              </label>

              {handed && (
                <>
                  <p className="-mt-2 text-xs text-muted-foreground">
                    This adds {direction === "receivable" ? "an expense" : "an income"} of the amount
                    above. It is not a payment, so it does not reduce what is owed.
                  </p>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="origin-account">Account</Label>
                    <NativeSelect
                      id="origin-account"
                      value={accountId}
                      onChange={(e) => setOriginAccountId(e.target.value)}
                    >
                      {openAccounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                    </NativeSelect>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="origin-date">Date</Label>
                    <Input
                      id="origin-date"
                      type="date"
                      value={originDate}
                      onChange={(e) => setOriginDate(e.target.value)}
                      className="h-11"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="origin-category">Category (optional)</Label>
                    <NativeSelect
                      id="origin-category"
                      value={originCategoryId}
                      onChange={(e) => setOriginCategoryId(e.target.value)}
                    >
                      <option value="">Uncategorized</option>
                      {originCategories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.label}
                        </option>
                      ))}
                    </NativeSelect>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="origin-note">Note (optional)</Label>
                    <Input
                      id="origin-note"
                      value={originNote}
                      onChange={(e) => setOriginNote(e.target.value)}
                      autoComplete="off"
                      className="h-11"
                    />
                  </div>
                </>
              )}
            </div>
          )}

          <AttachmentList parent={editing ? { kind: "due", id: editing.id } : null} />

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
