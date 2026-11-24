"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { AttachmentList } from "@/components/attachments/attachment-list";
import { PartySelect } from "@/components/parties/party-select";
import { ProjectSelect } from "@/components/projects/project-select";
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
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { todayIn } from "@/lib/dates";
import { paiseToRupeesInput, rupeesToPaise } from "@/lib/format";
import { useAccounts } from "@/lib/queries/accounts";
import { useCategories } from "@/lib/queries/categories";
import { useMe } from "@/lib/queries/me";
import {
  useSaveTransaction,
  useSaveTransfer,
  useTransactions,
  useTransfer,
} from "@/lib/queries/transactions";
import { categoryItems, type CategoryItem, type Row } from "@/lib/transactions";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";
import { NativeSelect } from "./native-select";

type Kind = Row["kind"];

const KINDS: { value: Kind; label: string }[] = [
  { value: "expense", label: "Expense" },
  { value: "income", label: "Income" },
  { value: "transfer", label: "Transfer" },
];

// A stable empty filter: the dialog reads the newest page only to find recently used categories.
const NO_FILTERS = {};

// The same limits as the API, so most mistakes are caught before a request is sent.
const common = z.object({
  amountPaise: z
    .number({ error: "Enter an amount like 250 or 99.50" })
    .int()
    .positive("The amount must be greater than zero"),
  occurredOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Pick a date"),
  note: z.string().trim().max(500, "Use at most 500 characters"),
});

const entrySchema = common.extend({
  accountId: z.string().min(1, "Choose an account"),
  categoryId: z.string(),
  tags: z
    .array(z.string().max(50, "Each tag can have at most 50 characters"))
    .max(20, "Use at most 20 tags"),
});

const transferSchema = common
  .extend({
    fromAccountId: z.string().min(1, "Choose the account the money leaves"),
    toAccountId: z.string().min(1, "Choose the account the money goes to"),
  })
  .refine((v) => v.fromAccountId !== v.toAccountId, {
    message: "Choose two different accounts",
    path: ["toAccountId"],
  });

function EntryForm({ editing, onClose }: { editing: Row | null; onClose: () => void }) {
  const { active: workspace } = useWorkspace();
  const me = useMe();
  const accounts = useAccounts(workspace.id);
  const categories = useCategories(workspace.id);
  const recent = useTransactions(workspace.id, NO_FILTERS);
  const saveTransaction = useSaveTransaction(workspace.id);
  const saveTransfer = useSaveTransfer(workspace.id);

  const [kind, setKind] = useState<Kind>(editing?.kind ?? "expense");
  const [amount, setAmount] = useState(editing ? paiseToRupeesInput(editing.amountPaise) : "");
  const [categoryId, setCategoryId] = useState(editing?.categoryId ?? "");
  const [partyId, setPartyId] = useState(editing?.partyId ?? "");
  const [projectId, setProjectId] = useState(editing?.projectId ?? "");
  const [accountId, setAccountId] = useState(editing?.accountId ?? editing?.fromAccountId ?? "");
  const [toAccountId, setToAccountId] = useState(editing?.toAccountId ?? "");
  const [date, setDate] = useState(
    editing?.occurredOn ?? todayIn(me.data?.timezone ?? "Asia/Kolkata"),
  );
  const [note, setNote] = useState(editing?.note ?? "");
  const [tagText, setTagText] = useState(editing?.tags.join(", ") ?? "");
  const [error, setError] = useState<string | null>(null);

  // Accounts load after the dialog opens, so the defaults are worked out here instead of being
  // stored in state: the first open account, and for a transfer the next one as the target.
  const allAccounts = accounts.data ?? [];
  const openAccounts = allAccounts.filter((a) => !a.archived_at);
  const fromId = accountId || openAccounts[0]?.id || "";
  const toId = toAccountId || openAccounts.find((a) => a.id !== fromId)?.id || "";
  // An archived account stays in the list only while it is the current choice.
  const optionsFor = (selected: string) =>
    allAccounts.filter((a) => !a.archived_at || a.id === selected);
  const recentCategoryIds = [
    ...new Set(
      (recent.data?.pages[0]?.items ?? []).flatMap((t) =>
        t.type === kind && t.category_id ? [t.category_id] : [],
      ),
    ),
  ];
  const items =
    kind === "transfer"
      ? []
      : categoryItems(categories.data ?? [], kind, {
          recentIds: recentCategoryIds,
          keepId: editing?.categoryId,
        });
  const selectedCategory = items.find((i) => i.id === categoryId) ?? null;

  const pending = saveTransaction.isPending || saveTransfer.isPending;

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const callbacks = {
      onSuccess: () => {
        toast(editing ? "Changes saved" : kind === "transfer" ? "Transfer added" : "Transaction added");
        onClose();
      },
      onError: (e: Error) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    };
    const shared = {
      amountPaise: rupeesToPaise(amount) ?? undefined,
      occurredOn: date,
      note,
    };

    if (kind === "transfer") {
      const parsed = transferSchema.safeParse({ ...shared, fromAccountId: fromId, toAccountId: toId });
      if (!parsed.success) return setError(parsed.error.issues[0].message);
      const v = parsed.data;
      saveTransfer.mutate(
        {
          id: editing?.id,
          body: {
            from_account_id: v.fromAccountId,
            to_account_id: v.toAccountId,
            amount_paise: v.amountPaise,
            occurred_on: v.occurredOn,
            // Not shown in this form; sent back so an edit does not clear it (PUT replaces).
            payment_method: editing?.paymentMethod,
            note: v.note || undefined,
          },
        },
        callbacks,
      );
      return;
    }

    const tags = tagText
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);
    const parsed = entrySchema.safeParse({ ...shared, accountId: fromId, categoryId, tags });
    if (!parsed.success) return setError(parsed.error.issues[0].message);
    const v = parsed.data;
    saveTransaction.mutate(
      {
        id: editing?.id,
        body: {
          account_id: v.accountId,
          type: kind,
          amount_paise: v.amountPaise,
          occurred_on: v.occurredOn,
          category_id: kind === "expense" ? v.categoryId || undefined : undefined,
          party_id: partyId || undefined,
          project_id: projectId || undefined,
          // Not shown in this form; sent back so editing a payment does not unlink it (PUT replaces).
          due_id: editing?.dueId,
          payment_method: editing?.paymentMethod,
          note: v.note || undefined,
          tags: v.tags,
        },
      },
      callbacks,
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="entry-amount">Amount (₹)</Label>
        <Input
          id="entry-amount"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          inputMode="decimal"
          autoComplete="off"
          placeholder="0"
          className="h-12 text-lg tabular-nums"
        />
      </div>

      <fieldset disabled={!!editing} className="flex flex-col gap-1.5">
        <legend className="mb-1.5 text-sm font-medium">Type</legend>
        <div className="grid grid-cols-3 gap-1 rounded-md bg-subtle p-1">
          {KINDS.map((k) => (
            <label key={k.value} className="relative">
              <input
                type="radio"
                name="kind"
                value={k.value}
                checked={kind === k.value}
                onChange={() => {
                  setKind(k.value);
                  setCategoryId(""); // expense and income have different categories
                }}
                className="peer sr-only"
              />
              <span className="flex min-h-11 cursor-pointer items-center justify-center rounded-md text-sm font-medium text-muted-foreground peer-checked:bg-surface peer-checked:text-foreground peer-checked:shadow-sm peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50 peer-disabled:cursor-default peer-disabled:opacity-60">
                {k.label}
              </span>
            </label>
          ))}
        </div>
        {editing && (
          <p className="text-xs text-muted-foreground">
            The type cannot be changed after saving. Delete this one and add a new one instead.
          </p>
        )}
      </fieldset>

      {kind === "transfer" ? (
        <>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="entry-from">From account</Label>
            <NativeSelect id="entry-from" value={fromId} onChange={(e) => setAccountId(e.target.value)}>
              {optionsFor(fromId).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                  {a.archived_at ? " (archived)" : ""}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="entry-to">To account</Label>
            <NativeSelect id="entry-to" value={toId} onChange={(e) => setToAccountId(e.target.value)}>
              {optionsFor(toId).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                  {a.archived_at ? " (archived)" : ""}
                </option>
              ))}
            </NativeSelect>
          </div>
        </>
      ) : (
        <>
          {/* Income has no categories. */}
          {kind === "expense" && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="entry-category">Category</Label>
            <Combobox<CategoryItem>
              items={items}
              value={selectedCategory}
              onValueChange={(item) => setCategoryId(item?.id ?? "")}
              itemToStringLabel={(item) => item.label}
              isItemEqualToValue={(a, b) => a.id === b.id}
            >
              <ComboboxInput
                id="entry-category"
                placeholder="Uncategorized"
                showClear
                className="h-11 w-full"
              />
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
          )}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="entry-account">Account</Label>
            <NativeSelect
              id="entry-account"
              value={fromId}
              onChange={(e) => setAccountId(e.target.value)}
            >
              {optionsFor(fromId).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                  {a.archived_at ? " (archived)" : ""}
                </option>
              ))}
            </NativeSelect>
          </div>
          <PartySelect
            id="entry-party"
            value={partyId}
            onChange={setPartyId}
            keepId={editing?.partyId}
          />
          <ProjectSelect
            id="entry-project"
            value={projectId}
            onChange={setProjectId}
            keepId={editing?.projectId}
          />
        </>
      )}

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="entry-date">Date</Label>
        <Input
          id="entry-date"
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="h-11"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="entry-note">Note (optional)</Label>
        <Input
          id="entry-note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          autoComplete="off"
          className="h-11"
        />
      </div>

      {kind !== "transfer" && (
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="entry-tags">Tags (optional)</Label>
          <Input
            id="entry-tags"
            value={tagText}
            onChange={(e) => setTagText(e.target.value)}
            autoComplete="off"
            placeholder="Separate with commas"
            className="h-11"
          />
        </div>
      )}

      {kind !== "transfer" && (
        <AttachmentList parent={editing ? { kind: "transaction", id: editing.id } : null} />
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {/* Sticky so Save stays in reach while the long form scrolls (no Enter on a phone keypad). */}
      <DialogFooter className="sticky bottom-0 -mb-4 flex-row rounded-b-none bg-popover *:flex-1 md:rounded-b-xl md:*:flex-none">
        <Button type="button" variant="outline" className="h-11" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" className="h-11" disabled={pending}>
          {pending ? "Saving…" : "Save"}
        </Button>
      </DialogFooter>
    </form>
  );
}

/**
 * A transfer row can have only one of its two legs loaded (an account filter hides the other),
 * and the form needs both accounts, so in that case the whole transfer is fetched first.
 */
function Body({ editing, onClose }: { editing: Row | null; onClose: () => void }) {
  const { active } = useWorkspace();
  const incomplete = !!editing && editing.kind === "transfer" && !(editing.fromAccountId && editing.toAccountId);
  const full = useTransfer(active.id, editing?.id ?? "", incomplete);

  if (!incomplete || !editing) return <EntryForm editing={editing} onClose={onClose} />;
  if (full.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        We could not load this transfer. Close this and try again.
      </p>
    );
  }
  if (!full.data) return <Skeleton className="h-64 w-full" />;
  return (
    <EntryForm
      editing={{
        ...editing,
        fromAccountId: full.data.from_account_id,
        toAccountId: full.data.to_account_id,
      }}
      onClose={onClose}
    />
  );
}

/** One dialog for adding and editing. `session` changes on every open so the form starts fresh. */
export function QuickAddDialog({
  open,
  editing,
  session,
  onOpenChange,
}: {
  open: boolean;
  editing: Row | null;
  session: number;
  onOpenChange: (open: boolean) => void;
}) {
  const title = !editing ? "Add transaction" : editing.kind === "transfer" ? "Edit transfer" : "Edit transaction";
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Phone: a bottom sheet (top corners rounded, slides up). md and up: the centred dialog. */}
      <DialogContent
        className={cn(
          "top-auto bottom-0 left-0 max-h-[90dvh] max-w-full translate-x-0 translate-y-0 overflow-y-auto rounded-none rounded-t-2xl data-open:slide-in-from-bottom data-closed:slide-out-to-bottom",
          "md:top-1/2 md:bottom-auto md:left-1/2 md:max-h-[calc(100dvh-2rem)] md:max-w-md md:-translate-x-1/2 md:-translate-y-1/2 md:rounded-2xl md:data-open:slide-in-from-bottom-0 md:data-closed:slide-out-to-bottom-0",
        )}
      >
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription className="sr-only">
            Enter the amount and details. Press Enter to save or Escape to close.
          </DialogDescription>
        </DialogHeader>
        <Body key={session} editing={editing} onClose={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}
