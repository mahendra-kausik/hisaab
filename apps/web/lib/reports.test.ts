import { describe, expect, it } from "vitest";
import { percentChange } from "./reports";

describe("percentChange", () => {
  it("is null when the previous period is empty", () => {
    expect(percentChange(500, 0)).toBeNull();
  });

  it("rounds to whole percents, up and down", () => {
    expect(percentChange(150, 100)).toBe(50);
    expect(percentChange(50, 100)).toBe(-50);
    expect(percentChange(1, 3)).toBe(-67);
    expect(percentChange(100, 100)).toBe(0);
    // A tiny drop rounds to 0, never to -0.
    expect(percentChange(999, 1000)).toBe(0);
  });

  it("keeps its meaning when the previous net was negative", () => {
    // Net improved from -100 to -50: that is a +50% change.
    expect(percentChange(-50, -100)).toBe(50);
    expect(percentChange(-150, -100)).toBe(-50);
  });
});
