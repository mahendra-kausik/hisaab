import { describe, expect, it } from "vitest";
import { budgetState, periodLabel, shiftDay } from "./budgets";

describe("budgetState", () => {
  it("is ok below 80%", () => expect(budgetState(7999, 10000)).toBe("ok"));
  it("warns from 80%", () => expect(budgetState(8000, 10000)).toBe("warning"));
  it("is over at exactly 100%", () => expect(budgetState(10000, 10000)).toBe("over"));
  it("is over beyond 100%", () => expect(budgetState(15000, 10000)).toBe("over"));
});

describe("shiftDay", () => {
  it("crosses month and year boundaries", () => {
    expect(shiftDay("2026-11-30", 1)).toBe("2026-12-01");
    expect(shiftDay("2026-01-01", -1)).toBe("2025-12-31");
  });
});

describe("periodLabel", () => {
  it("shows the year once", () =>
    expect(periodLabel("2026-11-01", "2026-11-30")).toBe("1 Nov – 30 Nov 2026"));
});
