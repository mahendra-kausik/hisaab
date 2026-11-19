export type Frequency = "weekly" | "monthly" | "yearly";

export const FREQUENCIES: { value: Frequency; label: string }[] = [
  { value: "weekly", label: "Weekly" },
  { value: "monthly", label: "Monthly" },
  { value: "yearly", label: "Yearly" },
];

const UNIT: Record<Frequency, string> = { weekly: "week", monthly: "month", yearly: "year" };

/** "Every month", "Every 2 weeks". */
export function frequencyLabel(frequency: Frequency, interval: number): string {
  return interval === 1
    ? `Every ${UNIT[frequency]}`
    : `Every ${interval} ${UNIT[frequency]}s`;
}
