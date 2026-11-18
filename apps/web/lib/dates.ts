/**
 * Date helpers. Calendar dates travel as "YYYY-MM-DD" strings (the API's `occurred_on`), never
 * as `Date` objects: a `Date` is an instant, and converting it to a day depends on the
 * browser's time zone. Day arithmetic here uses UTC only to count whole days.
 */

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function parts(iso: string): [number, number, number] {
  const [y, m, d] = iso.split("-").map(Number);
  return [y, m, d];
}

function dayNumber(iso: string): number {
  const [y, m, d] = parts(iso);
  return Date.UTC(y, m - 1, d) / 86_400_000;
}

/** Today's date in the given IANA time zone, e.g. "Asia/Kolkata" → "2026-11-19". */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  // The "en-CA" locale formats dates as YYYY-MM-DD.
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/** "Today", "Yesterday", or "4 Nov 2026". */
export function formatDate(iso: string, today: string): string {
  const daysAgo = dayNumber(today) - dayNumber(iso);
  if (daysAgo === 0) return "Today";
  if (daysAgo === 1) return "Yesterday";
  const [y, m, d] = parts(iso);
  return `${d} ${MONTHS[m - 1]} ${y}`;
}
