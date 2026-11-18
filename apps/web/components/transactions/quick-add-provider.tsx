"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { Row } from "@/lib/transactions";
import { QuickAddDialog } from "./quick-add-dialog";

type QuickAdd = {
  openAdd: () => void;
  openEdit: (row: Row) => void;
};

const QuickAddContext = createContext<QuickAdd | null>(null);

// Typing "n" in a field must not open the dialog.
function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
  );
}

/**
 * Owns the one quick-add dialog for every signed-in page, and the `N` shortcut that opens it.
 * Pages and the sidebar call `useQuickAdd()` to open it for a new entry or to edit a row.
 */
export function QuickAddProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Row | null>(null);
  const [session, setSession] = useState(0);

  const openAdd = useCallback(() => {
    setEditing(null);
    setSession((s) => s + 1);
    setOpen(true);
  }, []);
  const openEdit = useCallback((row: Row) => {
    setEditing(row);
    setSession((s) => s + 1);
    setOpen(true);
  }, []);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key.toLowerCase() !== "n" || event.repeat) return;
      if (event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return;
      if (isTyping(event.target)) return;
      // Another dialog or an open menu has the keyboard: leave it alone.
      if (document.querySelector('[role="dialog"], [role="menu"]')) return;
      event.preventDefault(); // so the "n" is not typed into the amount field that gets focus
      openAdd();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [openAdd]);

  const value = useMemo(() => ({ openAdd, openEdit }), [openAdd, openEdit]);

  return (
    <QuickAddContext.Provider value={value}>
      {children}
      <QuickAddDialog open={open} editing={editing} session={session} onOpenChange={setOpen} />
    </QuickAddContext.Provider>
  );
}

export function useQuickAdd(): QuickAdd {
  const value = useContext(QuickAddContext);
  if (!value) throw new Error("useQuickAdd must be used inside <QuickAddProvider>");
  return value;
}
