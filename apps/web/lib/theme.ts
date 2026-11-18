import { useSyncExternalStore } from "react";
import { THEME_STORAGE_KEY } from "./theme-init";

export type Theme = "system" | "light" | "dark";

const listeners = new Set<() => void>();

// The <html> class is the source of truth: the init script set it from storage, and
// setTheme keeps it in sync. Reading it also works when storage is blocked.
function getTheme(): Theme {
  const classes = document.documentElement.classList;
  if (classes.contains("dark")) return "dark";
  if (classes.contains("light")) return "light";
  return "system";
}

export function setTheme(theme: Theme) {
  const classes = document.documentElement.classList;
  classes.remove("light", "dark");
  if (theme !== "system") classes.add(theme);
  try {
    if (theme === "system") window.localStorage.removeItem(THEME_STORAGE_KEY);
    else window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Not remembered across reloads; it still applies to this visit.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The current choice. The server and the first client render both say "system". */
export function useTheme(): Theme {
  return useSyncExternalStore(subscribe, getTheme, () => "system");
}
