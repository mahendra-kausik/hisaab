import type { components } from "@/lib/api/schema";
import { shiftDay } from "./budgets";
import { formatDate } from "./dates";

type Due = components["schemas"]["DueResponse"];

export type DueSection = "overdue" | "today" | "upcoming" | "later" | "settled" | "cancelled";

/** Display order of the Dues screen. */
export const DUE_SECTIONS: { key: DueSection; label: string }[] = [
  { key: "overdue", label: "Overdue" },
  { key: "today", label: "Due today" },
  { key: "upcoming", label: "Upcoming (next 7 days)" },
  { key: "later", label: "Later" },
  { key: "settled", label: "Settled" },
  { key: "cancelled", label: "Cancelled" },
];

export const STATE_LABEL: Record<Due["state"], string> = {
  open: "Open",
  partially_paid: "Partially paid",
  settled: "Settled",
  overdue: "Overdue",
  cancelled: "Cancelled",
};

/** Which block of the Dues screen a due belongs to. `today` is "YYYY-MM-DD" in the user's zone. */
export function dueSection(
  due: Pick<Due, "state" | "due_date">,
  today: string,
): DueSection {
  if (due.state === "cancelled") return "cancelled";
  if (due.state === "settled") return "settled";
  if (!due.due_date) return "later";
  if (due.due_date < today) return "overdue";
  if (due.due_date === today) return "today";
  return due.due_date <= shiftDay(today, 7) ? "upcoming" : "later";
}

/** "Due today", "in 3 days", "5 days overdue", or the date when it is further than a week away. */
export function dueWhen(dueDate: string | undefined, today: string): string {
  if (!dueDate) return "No due date";
  if (dueDate === today) return "Due today";
  const days = Math.round((Date.parse(dueDate) - Date.parse(today)) / 86_400_000);
  const n = Math.abs(days);
  if (days < 0) return n === 1 ? "1 day overdue" : `${n} days overdue`;
  if (days <= 7) return days === 1 ? "tomorrow" : `in ${days} days`;
  return formatDate(dueDate, today);
}

/**
 * What the party still owes the user, minus what the user still owes them. Positive: they owe
 * you. Cancelled dues are ignored.
 */
export function partyNet(dues: Pick<Due, "direction" | "state" | "outstanding_paise">[]): number {
  return dues.reduce((sum, d) => {
    if (d.state === "cancelled") return sum;
    return sum + (d.direction === "receivable" ? d.outstanding_paise : -d.outstanding_paise);
  }, 0);
}

/** Money paid out (expenses) and received (income) across transactions. Transfers are ignored. */
export function partyTotals(items: { type: string; amount_paise: number }[]): {
  paid: number;
  received: number;
} {
  let paid = 0;
  let received = 0;
  for (const t of items) {
    if (t.type === "expense") paid += t.amount_paise;
    else if (t.type === "income") received += t.amount_paise;
  }
  return { paid, received };
}
