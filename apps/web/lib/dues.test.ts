import { describe, expect, it } from "vitest";
import { dueSection, dueWhen, partyNet, partyTotals } from "./dues";

const TODAY = "2026-11-20";

describe("dueSection", () => {
  it("puts cancelled and settled first, whatever the date", () => {
    expect(dueSection({ state: "cancelled", due_date: "2026-01-01" }, TODAY)).toBe("cancelled");
    expect(dueSection({ state: "settled", due_date: "2026-01-01" }, TODAY)).toBe("settled");
  });

  it("splits by due date", () => {
    expect(dueSection({ state: "overdue", due_date: "2026-11-19" }, TODAY)).toBe("overdue");
    expect(dueSection({ state: "open", due_date: TODAY }, TODAY)).toBe("today");
    expect(dueSection({ state: "open", due_date: "2026-11-27" }, TODAY)).toBe("upcoming");
    expect(dueSection({ state: "open", due_date: "2026-11-28" }, TODAY)).toBe("later");
    expect(dueSection({ state: "partially_paid", due_date: undefined }, TODAY)).toBe("later");
  });
});

describe("dueWhen", () => {
  it("words the distance", () => {
    expect(dueWhen(TODAY, TODAY)).toBe("Due today");
    expect(dueWhen("2026-11-21", TODAY)).toBe("tomorrow");
    expect(dueWhen("2026-11-23", TODAY)).toBe("in 3 days");
    expect(dueWhen("2026-11-15", TODAY)).toBe("5 days overdue");
    expect(dueWhen("2026-11-19", TODAY)).toBe("1 day overdue");
    expect(dueWhen("2026-12-25", TODAY)).toBe("25 Dec 2026");
    expect(dueWhen(undefined, TODAY)).toBe("No due date");
  });
});

describe("partyNet", () => {
  it("is receivable outstanding minus payable outstanding, skipping cancelled", () => {
    expect(
      partyNet([
        { direction: "receivable", state: "open", outstanding_paise: 600_000 },
        { direction: "payable", state: "partially_paid", outstanding_paise: 100_000 },
        { direction: "receivable", state: "cancelled", outstanding_paise: 999_999 },
      ]),
    ).toBe(500_000);
  });
});

describe("partyTotals", () => {
  it("sums expenses as paid and income as received, ignoring transfers", () => {
    expect(
      partyTotals([
        { type: "expense", amount_paise: 1_000_000 },
        { type: "income", amount_paise: 400_000 },
        { type: "transfer_out", amount_paise: 5 },
      ]),
    ).toEqual({ paid: 1_000_000, received: 400_000 });
  });
});
