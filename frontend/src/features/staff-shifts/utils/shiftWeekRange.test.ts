import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getShiftRange,
  isIsoDate,
  isPastDate,
  rangeContains,
  rangeLabel,
  startOfWeek,
  stepAnchor,
  weekdayShort,
} from "./shiftWeekRange";

describe("shiftWeekRange", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("T2 là đầu tuần, CN là cuối tuần", () => {
    expect(startOfWeek("2026-10-20")).toBe("2026-10-19"); // thứ Ba
    expect(startOfWeek("2026-10-19")).toBe("2026-10-19"); // chính thứ Hai
    expect(startOfWeek("2026-10-25")).toBe("2026-10-19"); // Chủ nhật
    const range = getShiftRange("2026-10-22", "week");
    expect(range.days).toHaveLength(7);
    expect(weekdayShort(range.days[0])).toBe("T2");
    expect(weekdayShort(range.days[6])).toBe("CN");
  });

  it("kỳ vắt qua tháng và năm (30/12 -> 05/01)", () => {
    const range = getShiftRange("2026-12-31", "week");
    expect(range.from).toBe("2026-12-28");
    expect(range.to).toBe("2027-01-03");
    const crossing = getShiftRange("2025-12-30", "week");
    expect(crossing.from).toBe("2025-12-29");
    expect(crossing.to).toBe("2026-01-04");
    expect(rangeLabel("2025-12-30", "week")).toBe("29/12/2025 – 04/01/2026");
    expect(rangeLabel("2026-10-20", "week")).toBe("19/10 – 25/10/2026");
  });

  it("kỳ Ngày chỉ có một ngày neo; ‹ › lùi tiến đúng độ rộng", () => {
    expect(getShiftRange("2026-10-20", "day").days).toEqual(["2026-10-20"]);
    expect(rangeLabel("2026-10-20", "day")).toBe("T3, 20/10/2026");
    expect(stepAnchor("2026-10-20", "week", 1)).toBe("2026-10-27");
    expect(stepAnchor("2026-10-20", "day", -1)).toBe("2026-10-19");
    expect(stepAnchor("2026-01-01", "day", -1)).toBe("2025-12-31");
  });

  it("so sánh ngày và kiểm tra định dạng", () => {
    expect(isPastDate("2026-10-19", "2026-10-20")).toBe(true);
    expect(isPastDate("2026-10-20", "2026-10-20")).toBe(false);
    expect(
      rangeContains(getShiftRange("2026-10-20", "week"), "2026-10-25"),
    ).toBe(true);
    expect(
      rangeContains(getShiftRange("2026-10-20", "week"), "2026-10-26"),
    ).toBe(false);
    expect(isIsoDate("2026-02-30")).toBe(false);
    expect(isIsoDate("20/10/2026")).toBe(false);
    expect(isIsoDate("2026-10-20")).toBe(true);
  });

  it("không lệch ngày khi múi giờ máy khác nhau", () => {
    for (const tz of ["UTC", "Asia/Ho_Chi_Minh", "America/Los_Angeles"]) {
      vi.stubEnv("TZ", tz);
      expect(startOfWeek("2026-10-20")).toBe("2026-10-19");
      expect(weekdayShort("2026-10-25")).toBe("CN");
    }
  });
});
