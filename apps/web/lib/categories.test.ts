import { describe, expect, it } from "vitest";
import { categoryTree } from "./categories";

describe("categoryTree", () => {
  const categories = [
    { id: "food", name: "Food", kind: "expense", is_system: true, sort_order: 0 },
    { id: "groc", parent_id: "food", name: "Groceries", kind: "expense", is_system: true, sort_order: 0 },
    { id: "eat", parent_id: "food", name: "Eating Out", kind: "expense", is_system: true, sort_order: 1 },
    { id: "fuel", name: "Fuel", kind: "expense", is_system: false, sort_order: 1 },
    { id: "pay", name: "Salary", kind: "income", is_system: true, sort_order: 0 },
  ] as Parameters<typeof categoryTree>[0];

  it("groups children under their parent, keeps the order and keeps the kinds apart", () => {
    const expense = categoryTree(categories, "expense");
    expect(expense.map((n) => [n.parent.id, n.children.map((c) => c.id)])).toEqual([
      ["food", ["groc", "eat"]],
      ["fuel", []],
    ]);
    expect(categoryTree(categories, "income").map((n) => n.parent.id)).toEqual(["pay"]);
  });
});
