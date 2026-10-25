import { describe, expect, it } from "vitest";
import { isBillingDateRangeValid } from "./billingDateRangeRules";

describe("isBillingDateRangeValid", () => {
  it("từ sau đến thì sai", () => {
    expect(isBillingDateRangeValid("2026-10-20", "2026-10-01")).toBe(false);
  });
  it("từ bằng đến thì hợp lệ (gồm cả hai đầu)", () => {
    expect(isBillingDateRangeValid("2026-10-20", "2026-10-20")).toBe(true);
  });
  it("từ trước đến thì hợp lệ", () => {
    expect(isBillingDateRangeValid("2026-09-20", "2026-10-20")).toBe(true);
  });
  it("thiếu một đầu hoặc cả hai thì hợp lệ", () => {
    expect(isBillingDateRangeValid("2026-10-20", undefined)).toBe(true);
    expect(isBillingDateRangeValid("", "2026-10-01")).toBe(true);
    expect(isBillingDateRangeValid()).toBe(true);
  });
});
