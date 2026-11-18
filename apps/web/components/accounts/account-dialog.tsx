"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
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
import { NativeSelect } from "@/components/transactions/native-select";
import { ApiError } from "@/lib/api/client";
import { paiseToRupeesInput, signedRupeesToPaise } from "@/lib/format";
import { useCreateAccount, useUpdateAccount, type Account } from "@/lib/queries/accounts";
import { useWorkspace } from "@/lib/workspace";
import { ACCOUNT_TYPES } from "./account-types";

// The same limits as the API (CreateAccountRequest), so most mistakes are caught before a request.
const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(100, "Use at most 100 characters"),
  type: z.enum(["cash", "bank", "credit_card", "wallet"]),
});

/** Add (`editing` is null) or edit an account. Mount it only while it is open, so the form starts fresh. */
export function AccountDialog({
  editing,
  onClose,
}: {
  editing: Account | null;
  onClose: () => void;
}) {
  const { active } = useWorkspace();
  const create = useCreateAccount(active.id);
  const update = useUpdateAccount(active.id);
  const [error, setError] = useState<string | null>(null);
  const pending = create.isPending || update.isPending;

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    const parsed = schema.safeParse(form);
    if (!parsed.success) return setError(parsed.error.issues[0].message);

    // An empty opening balance means 0.
    const text = String(form.opening ?? "").trim();
    const opening = text === "" ? 0 : signedRupeesToPaise(text);
    if (opening === null) return setError("Enter an amount like 5000 or -12000.50");

    setError(null);
    const body = {
      name: parsed.data.name,
      type: parsed.data.type,
      opening_balance_paise: opening,
    };
    const callbacks = {
      onSuccess: () => {
        toast(editing ? "Account updated" : "Account added");
        onClose();
      },
      onError: (e: Error) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    };
    if (editing) update.mutate({ id: editing.id, body }, callbacks);
    else create.mutate(body, callbacks);
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="rounded-2xl sm:max-w-md">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit account" : "Add account"}</DialogTitle>
            <DialogDescription>
              A place your money is kept, such as a bank account, a card or your wallet.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="account-name">Name</Label>
            <Input
              id="account-name"
              name="name"
              defaultValue={editing?.name}
              autoComplete="off"
              autoFocus
              placeholder="HDFC Bank"
              className="h-11"
              aria-invalid={error ? true : undefined}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="account-type">Type</Label>
            <NativeSelect id="account-type" name="type" defaultValue={editing?.type ?? "bank"}>
              {ACCOUNT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </NativeSelect>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="account-opening">Opening balance (₹)</Label>
            <Input
              id="account-opening"
              name="opening"
              defaultValue={editing ? paiseToRupeesInput(editing.opening_balance_paise) : ""}
              inputMode="decimal"
              autoComplete="off"
              placeholder="0"
              className="h-11 tabular-nums"
              aria-describedby="account-opening-hint"
            />
            <p id="account-opening-hint" className="text-xs text-muted-foreground">
              The money in it when you start. Use a minus sign for money owed, such as a credit card
              (−12000).
            </p>
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
            <Button type="submit" className="h-11" disabled={pending}>
              {pending ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
