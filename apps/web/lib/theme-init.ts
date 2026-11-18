// Kept apart from theme.ts: the server layout imports this, and theme.ts holds a React hook
// that only works in client components.

export const THEME_STORAGE_KEY = "hisaab.theme";

/**
 * Runs in <head> before the page paints (see app/layout.tsx), so a saved choice never
 * flashes the wrong colors. "system" is stored as no value and adds no class, which lets
 * the CSS follow the OS setting.
 */
export const THEME_INIT_SCRIPT = `try{var t=localStorage.getItem("${THEME_STORAGE_KEY}");if(t==="light"||t==="dark")document.documentElement.classList.add(t)}catch(e){}`;
