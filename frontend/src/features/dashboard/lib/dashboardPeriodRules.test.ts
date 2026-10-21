import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  DASHBOARD_MAX_RANGE_DAYS,
  periodLengthDays,
  previousPeriodOf,
  quickPeriodRange,
  todayInVietnam,
  validateDashboardPeriod,
} from "./dashboardPeriodRules";

const TODAY = "2026-10-20";

describe("dashboardPeriodRules", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-20T05:00:00Z"));
  });
  afterEach(() => vi.useRealTimers());

  it("todayInVietnam theo múi VN (17:30 UTC đã sang ngày mới)", () => {
    expect(todayInVietnam()).toBe(TODAY);
    vi.setSystemTime(new Date("2026-10-20T17:30:00Z"));
    expect(todayInVietnam()).toBe("2026-10-21");
  });

  it("4 kỳ nhanh gồm cả hôm nay", () => {
    expect(quickPeriodRange("today", TODAY)).toEqual({ from: TODAY, to: TODAY });
    expect(quickPeriodRange("7d", TODAY)).toEqual({ from: "2026-10-14", to: TODAY });
    expect(quickPeriodRange("30d", TODAY)).toEqual({ from: "2026-09-21", to: TODAY });
    expect(quickPeriodRange("month", TODAY)).toEqual({ from: "2026-10-01", to: TODAY });
  });

  it("đếm ngày gồm hai đầu", () => {
    expect(periodLengthDays({ from: TODAY, to: TODAY })).toBe(1);
    expect(periodLengthDays({ from: "2026-10-14", to: TODAY })).toBe(7);
  });

  it("kỳ trước cùng số ngày, liền trước", () => {
    expect(previousPeriodOf({ from: "2026-10-14", to: TODAY })).toEqual({
      from: "2026-10-07",
      to: "2026-10-13",
    });
    expect(previousPeriodOf({ from: TODAY, to: TODAY })).toEqual({
      from: "2026-10-19",
      to: "2026-10-19",
    });
  });

  it("kỳ hợp lệ trả null", () => {
    expect(validateDashboardPeriod({ from: "2026-10-14", to: TODAY })).toBeNull();
  });

  it("rỗng, từ > đến, sau hôm nay, vượt tối đa đều trả MSG-72", () => {
    expect(validateDashboardPeriod({ from: "", to: TODAY })).toBe("MSG-72");
    expect(validateDashboardPeriod({ from: TODAY, to: "" })).toBe("MSG-72");
    expect(validateDashboardPeriod({ from: TODAY, to: "2026-10-19" })).toBe("MSG-72");
    expect(validateDashboardPeriod({ from: "2026-10-14", to: "2026-10-21" })).toBe("MSG-72");
    expect(DASHBOARD_MAX_RANGE_DAYS).toBe(366);
    // 366 ngày (gồm cả hai đầu) là vừa đủ.
    expect(validateDashboardPeriod({ from: "2025-10-19", to: TODAY })).toBe("MSG-72");
    expect(validateDashboardPeriod({ from: "2025-10-20", to: TODAY })).toBeNull();
  });
});
