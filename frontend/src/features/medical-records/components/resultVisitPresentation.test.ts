import { describe, expect, it } from "vitest";
import type { ResultVisitSummary } from "../types/examinationResult";
import {
  countResults,
  filterByTab,
  groupByMonth,
  isInTimeRange,
  visitDateParts,
} from "./resultVisitPresentation";

const patient = { id: "p1", fullName: "An", relationship: "SELF" } as const;
const visit = (
  visitId: string,
  visitDate: string,
  status: ResultVisitSummary["status"],
): ResultVisitSummary => ({
  visitId,
  visitDate,
  status,
  serviceName: "Khám",
  patient,
});

describe("resultVisitPresentation", () => {
  const items = [
    visit("a", "2026-06-12", "COMPLETED"),
    visit("b", "2026-10-23", "COMPLETED"),
    visit("c", "2026-10-02", "INCOMPLETE"),
  ];

  it("nhóm theo tháng, tháng và lượt trong tháng đều mới nhất trước", () => {
    const groups = groupByMonth(items);
    expect(groups.map((group) => group.label)).toEqual([
      "Tháng 10, 2026",
      "Tháng 6, 2026",
    ]);
    expect(groups[0].items.map((item) => item.visitId)).toEqual(["b", "c"]);
  });

  it("đếm và lọc theo trạng thái", () => {
    expect(countResults(items)).toEqual({
      total: 3,
      completed: 2,
      incomplete: 1,
    });
    expect(
      filterByTab(items, "INCOMPLETE").map((item) => item.visitId),
    ).toEqual(["c"]);
    expect(filterByTab(items, "ALL")).toHaveLength(3);
  });

  it("khoảng thời gian tính theo ngày hôm nay truyền vào", () => {
    const today = "2026-11-08";
    expect(isInTimeRange("2020-01-01", "ALL", today)).toBe(true);
    expect(isInTimeRange("2026-10-10", "LAST_30_DAYS", today)).toBe(true);
    expect(isInTimeRange("2026-10-01", "LAST_30_DAYS", today)).toBe(false);
    expect(isInTimeRange("2026-08-20", "LAST_90_DAYS", today)).toBe(true);
    expect(isInTimeRange("2026-06-12", "LAST_90_DAYS", today)).toBe(false);
    expect(isInTimeRange("2026-01-05", "THIS_YEAR", today)).toBe(true);
    expect(isInTimeRange("2025-12-31", "THIS_YEAR", today)).toBe(false);
  });

  it("ô ngày có tháng, ngày và thứ viết hoa", () => {
    expect(visitDateParts("2026-10-23")).toEqual({
      month: "10",
      day: "23",
      weekday: "Thứ Sáu",
    });
  });
});
