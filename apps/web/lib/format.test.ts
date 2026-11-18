import { describe, expect, it } from "vitest";
import {
  formatINR,
  formatINRCompact,
  paiseToRupeesInput,
  rupeesToPaise,
  signedRupeesToPaise,
} from "./format";

describe("signedRupeesToPaise", () => {
  it("reads a leading minus as money owed", () => {
    expect(signedRupeesToPaise("-12000")).toBe(-1_200_000);
    expect(signedRupeesToPaise("−1,000.50")).toBe(-100_050);
    expect(signedRupeesToPaise(" -5 ")).toBe(-500);
  });

  it("matches rupeesToPaise for unsigned input and gives 0, not -0", () => {
    expect(signedRupeesToPaise("250")).toBe(25_000);
    expect(Object.is(signedRupeesToPaise("-0"), 0)).toBe(true);
  });

  it("rejects a bare or doubled minus and other invalid input", () => {
    for (const bad of ["-", "--5", "", "-1.234", "abc"]) {
      expect(signedRupeesToPaise(bad), `"${bad}"`).toBeNull();
    }
  });

  it("round-trips negatives through paiseToRupeesInput", () => {
    for (const paise of [-1, -12_345, -1_200_000]) {
      expect(signedRupeesToPaise(paiseToRupeesInput(paise))).toBe(paise);
    }
  });
});

describe("paiseToRupeesInput", () => {
  it("keeps the sign for negatives", () => {
    expect(paiseToRupeesInput(-12_345)).toBe("-123.45");
    expect(paiseToRupeesInput(-1_200_000)).toBe("-12000");
  });

  it("writes whole rupees without decimals and keeps two digits for paise", () => {
    expect(paiseToRupeesInput(12_300)).toBe("123");
    expect(paiseToRupeesInput(12_345)).toBe("123.45");
    expect(paiseToRupeesInput(12_305)).toBe("123.05");
    expect(paiseToRupeesInput(5)).toBe("0.05");
  });

  it("round-trips through rupeesToPaise", () => {
    for (const paise of [1, 99, 100, 100_050, 123_456_789]) {
      expect(rupeesToPaise(paiseToRupeesInput(paise))).toBe(paise);
    }
  });
});

describe("formatINR", () => {
  it("uses Indian digit grouping", () => {
    expect(formatINR(12_345_600)).toBe("₹1,23,456");
    expect(formatINR(1_000_000_000)).toBe("₹1,00,00,000");
    expect(formatINR(100_000)).toBe("₹1,000");
  });

  it("shows paise only when non-zero", () => {
    expect(formatINR(10_000)).toBe("₹100");
    expect(formatINR(12_345)).toBe("₹123.45");
    expect(formatINR(150)).toBe("₹1.50");
    expect(formatINR(5)).toBe("₹0.05");
  });

  it("handles zero and negatives", () => {
    expect(formatINR(0)).toBe("₹0");
    expect(formatINR(-150_000)).toBe("-₹1,500");
    expect(formatINR(-1)).toBe("-₹0.01");
  });

  it("rejects non-integer paise", () => {
    expect(() => formatINR(1.5)).toThrow(RangeError);
    expect(() => formatINR(Number.NaN)).toThrow(RangeError);
  });
});

describe("formatINRCompact", () => {
  it("shows the full amount below one lakh", () => {
    expect(formatINRCompact(0)).toBe("₹0");
    expect(formatINRCompact(9_999_999)).toBe("₹99,999.99");
  });

  it("uses L from one lakh, with one decimal only when non-zero", () => {
    expect(formatINRCompact(10_000_000)).toBe("₹1L");
    expect(formatINRCompact(12_000_000)).toBe("₹1.2L");
    expect(formatINRCompact(100_000_000)).toBe("₹10L");
  });

  it("uses Cr from one crore", () => {
    expect(formatINRCompact(1_000_000_000)).toBe("₹1Cr");
    expect(formatINRCompact(3_400_000_000)).toBe("₹3.4Cr");
    expect(formatINRCompact(1_234_500_000_000)).toBe("₹1,234.5Cr");
  });

  it("truncates instead of rounding", () => {
    expect(formatINRCompact(99_999_999)).toBe("₹9.9L");
    expect(formatINRCompact(3_499_999_999)).toBe("₹3.4Cr");
  });

  it("keeps the sign for negatives", () => {
    expect(formatINRCompact(-12_000_000)).toBe("-₹1.2L");
  });
});

describe("rupeesToPaise", () => {
  it("converts exactly, with no floating-point error", () => {
    expect(rupeesToPaise("0.29")).toBe(29);
    expect(rupeesToPaise("19.99")).toBe(1999);
    expect(rupeesToPaise("100")).toBe(10_000);
    expect(rupeesToPaise("5.1")).toBe(510);
  });

  it("accepts comma grouping", () => {
    expect(rupeesToPaise("1,000.5")).toBe(100_050);
    expect(rupeesToPaise("12,34,567.89")).toBe(123_456_789);
  });

  it("trims whitespace and accepts zero", () => {
    expect(rupeesToPaise("  42 ")).toBe(4_200);
    expect(rupeesToPaise("0")).toBe(0);
  });

  it("rejects invalid input", () => {
    for (const bad of [
      "",
      "   ",
      "-5",
      "1.234",
      "abc",
      "1.2.3",
      "₹5",
      "1,0",
      ".5",
      "5.",
      "1e3",
    ]) {
      expect(rupeesToPaise(bad), `"${bad}"`).toBeNull();
    }
  });

  it("rejects amounts too large to store exactly", () => {
    expect(rupeesToPaise("99999999999999999")).toBeNull();
  });

  it("round-trips with formatINR", () => {
    expect(formatINR(rupeesToPaise("1,23,456.7") ?? 0)).toBe("₹1,23,456.70");
  });
});
