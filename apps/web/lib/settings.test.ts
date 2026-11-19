import { describe, expect, it } from "vitest";
import { changedSettings } from "./settings";

const me = { name: "Asha", timezone: "Asia/Kolkata", period_start_day: 1, dues_digest_enabled: true };

describe("changedSettings", () => {
  it("is empty when nothing changed", () => {
    expect(changedSettings(me, { ...me })).toEqual({});
  });

  it("returns only the changed fields", () => {
    expect(changedSettings(me, { ...me, period_start_day: 25, dues_digest_enabled: false })).toEqual({
      period_start_day: 25,
      dues_digest_enabled: false,
    });
  });
});
