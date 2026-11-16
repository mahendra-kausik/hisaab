/**
 * Money helpers. Money is always integer paise (1 rupee = 100 paise): it comes
 * from the API as paise, is shown with formatINR / formatINRCompact, and user
 * input is converted back with rupeesToPaise. Nothing here multiplies or
 * divides floating-point rupees, so there is no rounding drift.
 */

const RUPEE = "₹";
const PAISE_PER_LAKH = 10_000_000; // 1,00,000 rupees
const PAISE_PER_CRORE = 1_000_000_000; // 1,00,00,000 rupees

// "en-IN" gives Indian grouping: 1,23,456. We only ever format whole numbers.
const indianGrouping = new Intl.NumberFormat("en-IN", {
  maximumFractionDigits: 0,
});

function assertPaise(paise: number): void {
  if (!Number.isSafeInteger(paise)) {
    throw new RangeError(`paise must be a safe integer, got ${paise}`);
  }
}

/** ₹1,23,456 — paise are shown only when non-zero (₹1,234.50). */
export function formatINR(paise: number): string {
  assertPaise(paise);
  const sign = paise < 0 ? "-" : "";
  const abs = Math.abs(paise);
  const rupees = Math.floor(abs / 100);
  const remainder = abs % 100;

  const whole = `${sign}${RUPEE}${indianGrouping.format(rupees)}`;
  return remainder === 0 ? whole : `${whole}.${String(remainder).padStart(2, "0")}`;
}

/**
 * Short form for summaries: ₹1.2L, ₹10L, ₹3.4Cr. Below ₹1 lakh it is the full
 * formatINR output. The single decimal is truncated, never rounded, so a
 * summary can never overstate a total.
 */
export function formatINRCompact(paise: number): string {
  assertPaise(paise);
  const abs = Math.abs(paise);
  if (abs < PAISE_PER_LAKH) return formatINR(paise);

  const [unit, label] =
    abs >= PAISE_PER_CRORE ? [PAISE_PER_CRORE, "Cr"] : [PAISE_PER_LAKH, "L"];
  const whole = Math.floor(abs / unit);
  // Tenths of a unit, using integers only: remainder < unit <= 1e9, so
  // remainder * 10 stays far below Number.MAX_SAFE_INTEGER.
  const tenths = Math.floor(((abs % unit) * 10) / unit);

  const sign = paise < 0 ? "-" : "";
  const decimal = tenths === 0 ? "" : `.${tenths}`;
  return `${sign}${RUPEE}${indianGrouping.format(whole)}${decimal}${label}`;
}

// Digits, optionally with comma grouping (1,000 or 12,34,567), then at most two
// decimals. No sign, no currency symbol, no leading ".5".
const RUPEE_INPUT = /^(\d+|\d{1,3}(,\d{2,3})+)(\.(\d{1,2}))?$/;

/**
 * Converts what a user typed ("1,000.5") to paise (100050) by string parsing,
 * never float multiplication ("0.29" * 100 is 28.999999999999996 in JS).
 * Returns null for anything invalid so forms can show an error: empty text,
 * negatives, three or more decimals, non-numeric text, or an amount too large
 * to store exactly. "0" is valid (returns 0); "must be greater than zero" is a
 * form validation rule, not a parsing rule.
 */
export function rupeesToPaise(input: string): number | null {
  const match = RUPEE_INPUT.exec(input.trim());
  if (!match) return null;

  const rupees = Number(match[1].replaceAll(",", ""));
  const paisePart = (match[4] ?? "").padEnd(2, "0");
  const paise = rupees * 100 + Number(paisePart);

  return Number.isSafeInteger(paise) ? paise : null;
}
