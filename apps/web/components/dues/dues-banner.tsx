"use client";

import { useState } from "react";
import Link from "next/link";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { dueAlertText, dueAlerts } from "@/lib/dues";
import { todayIn } from "@/lib/dates";
import { useDues } from "@/lib/queries/dues";
import { useMe } from "@/lib/queries/me";
import { useWorkspace } from "@/lib/workspace";

// localStorage can throw (private windows), so access is wrapped; the banner then just returns.
const key = (workspaceId: string) => `hisaab:dues-banner:${workspaceId}`;

function dismissedOn(workspaceId: string): string | null {
  try {
    return window.localStorage.getItem(key(workspaceId));
  } catch {
    return null;
  }
}

/** Overdue / due-today count for the active workspace and the user's timezone. */
export function useDueAlerts() {
  const { active } = useWorkspace();
  const me = useMe();
  const dues = useDues(active.id);
  const today = todayIn(me.data?.timezone ?? "Asia/Kolkata");
  return { counts: dueAlerts(dues.data ?? [], today), today, workspaceId: active.id };
}

/** Dismissible Home banner; a dismissal lasts until the next day, per workspace. */
export function DuesBanner() {
  const { counts, today, workspaceId } = useDueAlerts();
  // Read after mount so the server and first client render agree (both render nothing).
  const [dismissed, setDismissed] = useState<Record<string, string | null>>({});
  const stored = workspaceId in dismissed ? dismissed[workspaceId] : undefined;
  const lastDismissed = stored === undefined && typeof window !== "undefined" ? dismissedOn(workspaceId) : stored;

  if ((!counts.overdue && !counts.today) || lastDismissed === today) return null;

  return (
    <div className="flex items-center gap-3 rounded-xl border bg-surface p-3 pl-4">
      <p role="status" className="flex-1 text-sm font-medium">
        {dueAlertText(counts)}
      </p>
      <Link href="/dues" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
        Review dues
      </Link>
      <Button
        variant="ghost"
        className="h-11 w-11 p-0"
        aria-label="Dismiss"
        onClick={() => {
          try {
            window.localStorage.setItem(key(workspaceId), today);
          } catch {
            // Hidden for this visit only.
          }
          setDismissed((d) => ({ ...d, [workspaceId]: today }));
        }}
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  );
}
