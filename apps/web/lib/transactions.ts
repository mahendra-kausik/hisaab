import type { components } from "@/lib/api/schema";

export type TransactionItem = components["schemas"]["TransactionResponse"];
export type PaymentMethod = NonNullable<TransactionItem["payment_method"]>;

/**
 * One line in the list. A transfer is stored as two rows (out and in) but shown as one, so
 * `id` is the transfer's id for a transfer and the transaction's id otherwise. That is the id
 * the edit, delete and restore calls take.
 */
export type Row = {
  kind: "expense" | "income" | "transfer";
  id: string;
  occurredOn: string;
  amountPaise: number;
  /** Expense and income. */
  accountId?: string;
  /** Transfers. One is missing when only one leg is in the list (for example an account filter). */
  fromAccountId?: string;
  toAccountId?: string;
  categoryId?: string;
  partyId?: string;
  projectId?: string;
  /** Set when this entry is a payment against a due; sent back on edit so it stays linked. */
  dueId?: string;
  note?: string;
  tags: string[];
  paymentMethod?: PaymentMethod;
};

export type DayGroup = { date: string; net: number; rows: Row[] };

export type CategoryItem = { id: string; label: string };

/**
 * The choices for a category picker: categories of this kind, labelled "Parent › Child", with
 * the `recentIds` first and the rest in the API's order. Hidden categories are left out unless
 * `includeHidden` is set (filters) or one is the current choice (`keepId`, when editing an old
 * transaction).
 */
export function categoryItems(
  categories: components["schemas"]["CategoryResponse"][],
  kind: "expense" | "income",
  { recentIds = [], keepId, includeHidden = false }: {
    recentIds?: string[];
    keepId?: string;
    includeHidden?: boolean;
  } = {},
): CategoryItem[] {
  const byId = new Map(categories.map((c) => [c.id, c]));
  const rank = (id: string) => {
    const i = recentIds.indexOf(id);
    return i === -1 ? recentIds.length : i;
  };

  // Hiding a parent hides its subcategories too.
  const hidden = (c: (typeof categories)[number]) =>
    !!c.hidden_at || (!!c.parent_id && !!byId.get(c.parent_id)?.hidden_at);

  return categories
    .filter((c) => c.kind === kind && (includeHidden || !hidden(c) || c.id === keepId))
    .map((c) => {
      const parent = c.parent_id ? byId.get(c.parent_id) : undefined;
      return { id: c.id, label: parent ? `${parent.name} › ${c.name}` : c.name };
    })
    .sort((a, b) => rank(a.id) - rank(b.id)); // Array.sort is stable: ties keep the API order
}

/** Turns the API's items into display rows, merging a transfer's two legs into one row. */
export function toRows(items: TransactionItem[]): Row[] {
  const legs = new Map<string, { out?: TransactionItem; in?: TransactionItem }>();
  for (const t of items) {
    if (!t.transfer_group_id) continue;
    const group = legs.get(t.transfer_group_id) ?? {};
    if (t.type === "transfer_out") group.out = t;
    else group.in = t;
    legs.set(t.transfer_group_id, group);
  }

  const rows: Row[] = [];
  const merged = new Set<string>();
  for (const t of items) {
    const groupId = t.transfer_group_id;
    if (!groupId) {
      rows.push({
        kind: t.type === "income" ? "income" : "expense",
        id: t.id,
        occurredOn: t.occurred_on,
        amountPaise: t.amount_paise,
        accountId: t.account_id,
        categoryId: t.category_id,
        partyId: t.party_id,
        projectId: t.project_id,
        dueId: t.due_id,
        note: t.note,
        tags: t.tags,
        paymentMethod: t.payment_method,
      });
      continue;
    }
    if (merged.has(groupId)) continue;
    merged.add(groupId);

    const { out, in: inLeg } = legs.get(groupId)!;
    const leg = out ?? inLeg!;
    rows.push({
      kind: "transfer",
      id: groupId,
      occurredOn: leg.occurred_on,
      amountPaise: leg.amount_paise,
      fromAccountId: out?.account_id,
      toAccountId: inLeg?.account_id,
      note: leg.note,
      tags: [],
      paymentMethod: leg.payment_method,
    });
  }
  return rows;
}

/**
 * Groups rows (already newest first) by date. `net` is income minus expense in paise; transfers
 * are left out because they are neither income nor expense.
 */
export function groupByDate(rows: Row[]): DayGroup[] {
  const groups: DayGroup[] = [];
  for (const row of rows) {
    let group = groups[groups.length - 1];
    if (!group || group.date !== row.occurredOn) {
      group = { date: row.occurredOn, net: 0, rows: [] };
      groups.push(group);
    }
    group.rows.push(row);
    if (row.kind === "income") group.net += row.amountPaise;
    else if (row.kind === "expense") group.net -= row.amountPaise;
  }
  return groups;
}
