import { describe, expect, it } from "vitest";
import { safeNext } from "./safe-redirect";

describe("safeNext", () => {
  it("keeps paths on this site", () => {
    expect(safeNext("/transactions?type=expense")).toBe("/transactions?type=expense");
  });

  it("rejects anything that could leave the site", () => {
    for (const bad of ["https://evil.example", "//evil.example", "/\\evil.example", "evil", "", null, undefined]) {
      expect(safeNext(bad)).toBeNull();
    }
  });
});
