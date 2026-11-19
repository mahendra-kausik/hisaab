/**
 * Change versus the previous period as a whole percent (integer maths, rounded half away from
 * zero). Null when the previous period had nothing, because a percentage of zero means nothing.
 */
export function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return null;
  const diff = (current - previous) * 100;
  // `+ 0` turns a rounded-away negative (-0) into plain 0 so it never prints as "-0%".
  return Math.sign(diff) * Math.round(Math.abs(diff) / Math.abs(previous)) + 0;
}
