export type BudgetState = "ok" | "warning" | "over";

/** Warning from 80% of the budget, over at 100% (integer maths: no float rounding on money). */
export function budgetState(spentPaise: number, amountPaise: number): BudgetState {
  if (spentPaise >= amountPaise) return "over";
  if (spentPaise * 100 >= amountPaise * 80) return "warning";
  return "ok";
}

/** "2026-11-30" + 1 → "2026-12-01". UTC only counts whole days; no time zone is involved. */
export function shiftDay(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** "1 Nov – 30 Nov 2026" for a period's first and last day. */
export function periodLabel(start: string, end: string): string {
  const fmt = (iso: string, withYear: boolean) =>
    new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-IN", {
      timeZone: "UTC",
      day: "numeric",
      month: "short",
      ...(withYear ? { year: "numeric" } : {}),
    });
  return `${fmt(start, false)} – ${fmt(end, true)}`;
}
