import { describe, expect, it } from "vitest";
import { categoryItems, groupByDate, toRows, type TransactionItem } from "./transactions";

function item(overrides: Partial<TransactionItem>): TransactionItem {
  return {
    id: "t1",
    account_id: "a1",
    type: "expense",
    amount_paise: 10_000,
    occurred_on: "2026-11-19",
    tags: [],
    source: "manual",
    created_at: "2026-11-19T00:00:00Z",
    updated_at: "2026-11-19T00:00:00Z",
    ...overrides,
  };
}

describe("toRows", () => {
  it("merges a transfer's two legs into one row keyed by the transfer id", () => {
    const rows = toRows([
      item({ id: "out", type: "transfer_out", account_id: "bank", transfer_group_id: "g1" }),
      item({ id: "in", type: "transfer_in", account_id: "cash", transfer_group_id: "g1" }),
      item({ id: "e1", type: "expense" }),
    ]);

    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      kind: "transfer",
      id: "g1",
      fromAccountId: "bank",
      toAccountId: "cash",
    });
    expect(rows[1]).toMatchObject({ kind: "expense", id: "e1", accountId: "a1" });
  });

  it("keeps a lone leg as a transfer row with one account", () => {
    const rows = toRows([
      item({ id: "in", type: "transfer_in", account_id: "cash", transfer_group_id: "g1" }),
    ]);

    expect(rows).toEqual([
      expect.objectContaining({ kind: "transfer", id: "g1", toAccountId: "cash" }),
    ]);
    expect(rows[0].fromAccountId).toBeUndefined();
  });
});

describe("categoryItems", () => {
  const categories = [
    { id: "food", name: "Food", kind: "expense", is_system: true, sort_order: 1 },
    { id: "groc", parent_id: "food", name: "Groceries", kind: "expense", is_system: true, sort_order: 2 },
    { id: "fuel", name: "Fuel", kind: "expense", is_system: true, sort_order: 3 },
    { id: "old", name: "Old", kind: "expense", is_system: false, sort_order: 4, hidden_at: "2026-01-01T00:00:00Z" },
    { id: "pay", name: "Salary", kind: "income", is_system: true, sort_order: 5 },
  ] as Parameters<typeof categoryItems>[0];

  it("lists only this kind, labels children with their parent and skips hidden ones", () => {
    const items = categoryItems(categories, "expense");
    expect(items.map((i) => i.label)).toEqual(["Food", "Food › Groceries", "Fuel"]);
  });

  it("puts recently used categories first and keeps the rest in order", () => {
    const items = categoryItems(categories, "expense", { recentIds: ["fuel", "groc"] });
    expect(items.map((i) => i.id)).toEqual(["fuel", "groc", "food"]);
  });

  it("keeps a hidden category when it is the current choice or when asked to", () => {
    expect(categoryItems(categories, "expense", { keepId: "old" }).map((i) => i.id)).toContain("old");
    expect(categoryItems(categories, "expense", { includeHidden: true })).toHaveLength(4);
  });
});

describe("groupByDate", () => {
  it("totals a day as income minus expense and ignores transfers", () => {
    const rows = toRows([
      item({ id: "i", type: "income", amount_paise: 200_000 }),
      item({ id: "e", type: "expense", amount_paise: 125_000 }),
      item({ id: "o", type: "transfer_out", transfer_group_id: "g", amount_paise: 999_900 }),
      item({ id: "n", type: "transfer_in", transfer_group_id: "g", amount_paise: 999_900 }),
      item({ id: "old", type: "expense", amount_paise: 5_000, occurred_on: "2026-11-18" }),
    ]);

    const groups = groupByDate(rows);

    expect(groups.map((g) => [g.date, g.net, g.rows.length])).toEqual([
      ["2026-11-19", 75_000, 3],
      ["2026-11-18", -5_000, 1],
    ]);
  });
});
