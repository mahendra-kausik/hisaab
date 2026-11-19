"use client";

import { useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { NativeSelect } from "@/components/transactions/native-select";
import { ApiError } from "@/lib/api/client";
import { useMe, useUpdateSettings, type Me } from "@/lib/queries/me";
import { changedSettings } from "@/lib/settings";

// Same limits as the API (UpdateSettingsRequest).
const schema = z.object({
  name: z.string().trim().min(1, "Enter a name").max(100, "Use at most 100 characters"),
  timezone: z.string().min(1),
  period_start_day: z.coerce.number().int().min(1).max(28),
});

const DAYS = Array.from({ length: 28 }, (_, i) => i + 1);

function timezones(current: string): string[] {
  const all = Intl.supportedValuesOf("timeZone");
  return all.includes(current) ? all : [current, ...all];
}

function SettingsForm({ me }: { me: Me }) {
  const update = useUpdateSettings();
  const [error, setError] = useState<string | null>(null);

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const parsed = schema.safeParse(Object.fromEntries(data));
    if (!parsed.success) return setError(parsed.error.issues[0].message);

    const body = changedSettings(me, {
      ...parsed.data,
      dues_digest_enabled: data.get("dues_digest_enabled") === "on",
    });
    if (Object.keys(body).length === 0) return toast("Nothing to save");

    setError(null);
    update.mutate(body, {
      onSuccess: () => toast("Settings saved"),
      onError: (e) =>
        setError(e instanceof ApiError ? e.message : "Something went wrong. Try again."),
    });
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-md flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="settings-name">Name</Label>
        <Input
          id="settings-name"
          name="name"
          defaultValue={me.name}
          autoComplete="name"
          className="h-11"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="settings-timezone">Timezone</Label>
        <NativeSelect id="settings-timezone" name="timezone" defaultValue={me.timezone}>
          {timezones(me.timezone).map((tz) => (
            <option key={tz} value={tz}>
              {tz}
            </option>
          ))}
        </NativeSelect>
        <p className="text-xs text-muted-foreground">Decides what “today” means in your lists.</p>
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="settings-period">Month starts on day</Label>
        <NativeSelect
          id="settings-period"
          name="period_start_day"
          defaultValue={String(me.period_start_day)}
        >
          {DAYS.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </NativeSelect>
        <p className="text-xs text-muted-foreground">
          Reports and budgets count a month from this day, for example the day your salary arrives.
        </p>
      </div>

      <label className="flex min-h-11 items-center gap-3 text-sm">
        <input
          type="checkbox"
          name="dues_digest_enabled"
          defaultChecked={me.dues_digest_enabled}
          className="h-5 w-5 accent-primary"
        />
        Send me a daily summary of dues
      </label>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      <Button type="submit" className="h-11 self-start" disabled={update.isPending}>
        {update.isPending ? "Saving…" : "Save"}
      </Button>
    </form>
  );
}

export function SettingsView() {
  const me = useMe();
  return (
    <div className="flex flex-col gap-6 p-4 md:p-6">
      <h1 className="text-xl font-semibold">Settings</h1>
      {me.data ? <SettingsForm me={me.data} /> : <Skeleton className="h-64 max-w-md" />}
    </div>
  );
}
