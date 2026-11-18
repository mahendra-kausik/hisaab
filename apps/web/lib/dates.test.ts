import { describe, expect, it } from "vitest";
import { formatDate, todayIn } from "./dates";

describe("todayIn", () => {
  it("uses the time zone's calendar day, not the UTC day", () => {
    // 20:00 UTC on 18 Nov is already 19 Nov (01:30) in India, but still 18 Nov in New York.
    const now = new Date("2026-11-18T20:00:00Z");
    expect(todayIn("Asia/Kolkata", now)).toBe("2026-11-19");
    expect(todayIn("America/New_York", now)).toBe("2026-11-18");
  });
});

describe("formatDate", () => {
  it("says Today and Yesterday", () => {
    expect(formatDate("2026-11-19", "2026-11-19")).toBe("Today");
    expect(formatDate("2026-11-18", "2026-11-19")).toBe("Yesterday");
  });

  it("knows yesterday across a month and a year boundary", () => {
    expect(formatDate("2026-10-31", "2026-11-01")).toBe("Yesterday");
    expect(formatDate("2025-12-31", "2026-01-01")).toBe("Yesterday");
  });

  it("writes other dates as 4 Nov 2026", () => {
    expect(formatDate("2026-11-04", "2026-11-19")).toBe("4 Nov 2026");
    expect(formatDate("2025-01-09", "2026-11-19")).toBe("9 Jan 2025");
  });

  it("writes a future date in full", () => {
    expect(formatDate("2026-11-20", "2026-11-19")).toBe("20 Nov 2026");
  });
});
