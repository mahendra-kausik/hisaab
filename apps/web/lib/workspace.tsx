"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { Me, WorkspaceSummary } from "@/lib/queries/me";

const STORAGE_KEY = "hisaab.workspace";

// localStorage can throw (private windows, blocked site data), so every access is wrapped.
function readStored(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStored(id: string) {
  try {
    window.localStorage.setItem(STORAGE_KEY, id);
  } catch {
    // Not remembered across reloads; the choice still works for this visit.
  }
}

type WorkspaceContextValue = {
  /** The workspace every page reads and writes. */
  active: WorkspaceSummary;
  workspaces: WorkspaceSummary[];
  setActive: (id: string) => void;
};

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

/**
 * Holds the active workspace. Rendered only after `me` has loaded, so it is always set.
 * The stored id is used when it is still one of the user's workspaces; otherwise the first
 * personal workspace (or the first workspace) is used.
 */
export function WorkspaceProvider({ me, children }: { me: Me; children: React.ReactNode }) {
  // The initial value is read lazily on the client only; this provider never renders on
  // the server because the shell waits for `me` (fetched in the browser).
  const [storedId, setStoredId] = useState<string | null>(readStored);

  const active =
    me.workspaces.find((w) => w.id === storedId) ??
    me.workspaces.find((w) => w.kind === "personal") ??
    me.workspaces[0];

  const setActive = useCallback((id: string) => {
    writeStored(id);
    setStoredId(id);
  }, []);

  const value = useMemo(
    () => ({ active, workspaces: me.workspaces, setActive }),
    [active, me.workspaces, setActive],
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace(): WorkspaceContextValue {
  const value = useContext(WorkspaceContext);
  if (!value) throw new Error("useWorkspace must be used inside <WorkspaceProvider>");
  return value;
}
