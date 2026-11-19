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
import { Textarea } from "@/components/ui/textarea";
import { NativeSelect } from "@/components/transactions/native-select";
import { ApiError } from "@/lib/api/client";
import {
  PARTY_ROLES,
  useCreateParty,
  useUpdateParty,
  type Party,
  type PartyRole,
} from "@/lib/queries/parties";
import { useWorkspace } from "@/lib/workspace";

// The same limits as the API (CreatePartyRequest), so most mistakes are caught before a request.
const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(100, "Use at most 100 characters"),
  phone: z.string().trim().max(20, "Use at most 20 characters"),
  role: z.enum(PARTY_ROLES.map((r) => r.value) as [PartyRole, ...PartyRole[]]),
  notes: z.string().trim().max(500, "Use at most 500 characters"),
});

/** Add (`editing` is null) or edit a party. Mount it only while it is open, so the form starts fresh. */
export function PartyDialog({ editing, onClose }: { editing: Party | null; onClose: () => void }) {
  const { active } = useWorkspace();
  const create = useCreateParty(active.id);
  const update = useUpdateParty(active.id);
  const [error, setError] = useState<string | null>(null);
  const pending = create.isPending || update.isPending;

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = schema.safeParse(Object.fromEntries(new FormData(event.currentTarget)));
    if (!parsed.success) return setError(parsed.error.issues[0].message);

    setError(null);
    // Empty strings clear phone and notes on edit; on create the API stores them as null.
    const body = parsed.data;
    const callbacks = {
      onSuccess: () => {
        toast(editing ? "Party updated" : "Party added");
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
            <DialogTitle>{editing ? "Edit party" : "Add party"}</DialogTitle>
            <DialogDescription>
              Someone you pay or get money from, such as a buyer, contractor or friend.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="party-name">Name</Label>
            <Input
              id="party-name"
              name="name"
              defaultValue={editing?.name}
              autoComplete="off"
              autoFocus
              placeholder="Ramesh Kumar"
              className="h-11"
              aria-invalid={error ? true : undefined}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="party-role">Role</Label>
            <NativeSelect id="party-role" name="role" defaultValue={editing?.role ?? "other"}>
              {PARTY_ROLES.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </NativeSelect>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="party-phone">Phone (optional)</Label>
            <Input
              id="party-phone"
              name="phone"
              type="tel"
              defaultValue={editing?.phone}
              autoComplete="off"
              className="h-11"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="party-notes">Notes (optional)</Label>
            <Textarea id="party-notes" name="notes" defaultValue={editing?.notes} rows={3} />
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
