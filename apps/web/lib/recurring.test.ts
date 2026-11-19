import { describe, expect, it } from "vitest";
import { frequencyLabel } from "./recurring";

describe("frequencyLabel", () => {
  it("is singular for an interval of 1", () => expect(frequencyLabel("monthly", 1)).toBe("Every month"));
  it("pluralises larger intervals", () => expect(frequencyLabel("weekly", 2)).toBe("Every 2 weeks"));
  it("handles years", () => expect(frequencyLabel("yearly", 1)).toBe("Every year"));
});
