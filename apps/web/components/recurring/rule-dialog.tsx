"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { NativeSelect } from "@/components/transactions/native-select";
import { Button } from "@/components/ui/button";
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/components/ui/combobox";
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
import { FREQUENCIES, type Frequency } from "@/lib/recurring";
import { useAccounts } from "@/lib/queries/accounts";
import { useCategories } from "@/lib/queries/categories";
import { useMe } from "@/lib/queries/me";
import { useSaveRule, type RecurringRule } from "@/lib/queries/recurring";
import { categoryItems, type CategoryItem } from "@/lib/transactions";
import { useWorkspace } from "@/lib/workspace";

type Kind = "expense" | "income";

// The same limits as the API, so most mistakes are caught before a request is sent.
const schema = z.object({
  amountPaise: z
    .number({ error: "Enter an amount like 250 or 99.50" })
    .int()
    .positive("The amount must be greater than zero"),
  accountId: z.string().min(1, "Choose an account"),
  interval: z.number({ error: "Enter how often, like 1 or 2" }).int().min(1, "Use at least 1").max(52, "Use at most 52"),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Pick a start date"),
  endDate: z.string(),
  note: z.string().trim().max(500, "Use at most 500 characters"),
});

function RuleForm({ editing, onClose }: { editing: RecurringRule | null; onClose: () => void }) {
  const { active: workspace } = useWorkspace();
  const me = useMe();
  const accounts = useAccounts(workspace.id);
  const categories = useCategories(workspace.id);
  const save = useSaveRule(workspace.id);

  const [kind, setKind] = useState<Kind>((editing?.type as Kind) ?? "expense");
  const [amount, setAmount] = useState(editing ? paiseToRupeesInput(editing.amount_paise) : "");
  const [categoryId, setCategoryId] = useState(editing?.category_id ?? "");
  const [accountId, setAccountId] = useState(editing?.account_id ?? "");
  const [frequency, setFrequency] = useState<Frequency>(editing?.frequency ?? "monthly");
  const [interval, setInterval] = useState(String(editing?.interval_count ?? 1));
  const [start, setStart] = useState(
    editing?.anchor_date ?? todayIn(me.data?.timezone ?? "Asia/Kolkata"),
  );
  const [end, setEnd] = useState(editing?.ends_on ?? "");
  const [note, setNote] = useState(editing?.note ?? "");
  const [error, setError] = useState<string | null>(null);

  // Accounts load after the dialog opens, so the default is worked out here, not kept in state.
  const allAccounts = accounts.data ?? [];
  const openAccounts = allAccounts.filter((a) => !a.archived_at);
  const selectedAccount = accountId || openAccounts[0]?.id || "";
  const accountOptions = allAccounts.filter((a) => !a.archived_at || a.id === selectedAccount);

  const items = categoryItems(categories.data ?? [], kind, { keepId: editing?.category_id });
  const selectedCategory = items.find((i) => i.id === categoryId) ?? null;

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const parsed = schema.safeParse({
      amountPaise: rupeesToPaise(amount) ?? undefined,
      accountId: selectedAccount,
      interval: interval.trim() === "" ? undefined : Number(interval),
      startDate: start,
      endDate: end,
      note,
    });
    if (!parsed.success) return setError(parsed.error.issues[0].message);
    const v = parsed.data;
    if (v.endDate && v.endDate < v.startDate) return setError("The end date is before the start date");

    save.mutate(
      {
        id: editing?.id,
        body: {
          account_id: v.accountId,
          type: kind,
          amount_paise: v.amountPaise,
          category_id: categoryId || undefined,
          note: v.note || undefined,
          frequency,
          interval_count: v.interval,
          anchor_date: v.startDate,
          ends_on: v.endDate || undefined,
        },
      },
      {
        onSuccess: () => {
          toast(editing ? "Changes saved" : "Recurring entry added");
          onClose();
        },
        onError: (e) =>
          setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
      },
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="rule-amount">Amount (₹)</Label>
        <Input
          id="rule-amount"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          inputMode="decimal"
          autoComplete="off"
          placeholder="0"
          className="h-12 text-lg tabular-nums"
        />
      </div>

      <fieldset className="flex flex-col gap-1.5">
        <legend className="mb-1.5 text-sm font-medium">Type</legend>
        <div className="grid grid-cols-2 gap-1 rounded-md bg-subtle p-1">
          {(["expense", "income"] as const).map((k) => (
            <label key={k} className="relative">
              <input
                type="radio"
                name="rule-kind"
                value={k}
                checked={kind === k}
                onChange={() => {
                  setKind(k);
                  setCategoryId(""); // expense and income have different categories
                }}
                className="peer sr-only"
              />
              <span className="flex min-h-11 cursor-pointer items-center justify-center rounded-md text-sm font-medium capitalize text-muted-foreground peer-checked:bg-surface peer-checked:text-foreground peer-checked:shadow-sm peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50">
                {k}
              </span>
            </label>
          ))}
        </div>
      </fieldset>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="rule-category">Category</Label>
        <Combobox<CategoryItem>
          items={items}
          value={selectedCategory}
          onValueChange={(item) => setCategoryId(item?.id ?? "")}
          itemToStringLabel={(item) => item.label}
          isItemEqualToValue={(a, b) => a.id === b.id}
        >
          <ComboboxInput id="rule-category" placeholder="Uncategorized" showClear className="h-11 w-full" />
          <ComboboxContent>
            <ComboboxEmpty>No category found</ComboboxEmpty>
            <ComboboxList>
              {(item: CategoryItem) => (
                <ComboboxItem key={item.id} value={item} className="min-h-9">
                  {item.label}
                </ComboboxItem>
              )}
            </ComboboxList>
          </ComboboxContent>
        </Combobox>
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="rule-account">Account</Label>
        <NativeSelect id="rule-account" value={selectedAccount} onChange={(e) => setAccountId(e.target.value)}>
          {accountOptions.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
              {a.archived_at ? " (archived)" : ""}
            </option>
          ))}
        </NativeSelect>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="rule-frequency">Repeats</Label>
          <NativeSelect
            id="rule-frequency"
            value={frequency}
            onChange={(e) => setFrequency(e.target.value as Frequency)}
          >
            {FREQUENCIES.map((f) => (
              <option key={f.value} value={f.value}>
                {f.label}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="rule-interval">Every</Label>
          <Input
            id="rule-interval"
            value={interval}
            onChange={(e) => setInterval(e.target.value)}
            inputMode="numeric"
            autoComplete="off"
            className="h-11"
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="rule-start">First date</Label>
          <Input id="rule-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} className="h-11" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="rule-end">Last date (optional)</Label>
          <Input id="rule-end" type="date" value={end} onChange={(e) => setEnd(e.target.value)} className="h-11" />
        </div>
      </div>
      <p className="-mt-2 text-xs text-muted-foreground">
        A first date in the past adds the entries that were missed.
      </p>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="rule-note">Note (optional)</Label>
        <Input id="rule-note" value={note} onChange={(e) => setNote(e.target.value)} autoComplete="off" className="h-11" />
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
  );
}

/** Mounted only while open, so the form starts fresh each time. */
export function RuleDialog({ editing, onClose }: { editing: RecurringRule | null; onClose: () => void }) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit recurring entry" : "Add recurring entry"}</DialogTitle>
          <DialogDescription className="sr-only">
            Set the amount and how often it repeats. Press Enter to save or Escape to close.
          </DialogDescription>
        </DialogHeader>
        <RuleForm editing={editing} onClose={onClose} />
      </DialogContent>
    </Dialog>
  );
}
