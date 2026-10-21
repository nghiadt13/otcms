import { describe, expect, it } from "vitest";
import { MOCK_SUBJECTS } from "../api/reportMockData";
import {
  EMPTY_REPORT_FORM,
  normalizeBreakdown,
  toReportQuery,
  validateReportForm,
} from "./reportFieldRules";

const valid = {
  subject: "PAYMENTS" as const,
  from: "2026-10-01",
  to: "2026-10-20",
  breakdown: "DATE" as const,
};

describe("validateReportForm", () => {
  it("form trống: MSG-72 ở chủ đề và hai ô ngày", () => {
    expect(validateReportForm(EMPTY_REPORT_FORM, MOCK_SUBJECTS)).toEqual({
      subject: "MSG-72",
      from: "MSG-72",
      to: "MSG-72",
    });
  });

  it("from > to: MSG-72 ở to", () => {
    expect(
      validateReportForm({ ...valid, from: "2026-10-20", to: "2026-10-01" }, MOCK_SUBJECTS),
    ).toEqual({ to: "MSG-72" });
  });

  it("chủ đề ngoài danh sách được phép là không hợp lệ", () => {
    const allowed = MOCK_SUBJECTS.filter((s) => s.code !== "PAYMENTS");
    expect(validateReportForm(valid, allowed)).toEqual({ subject: "MSG-72" });
  });

  it("form hợp lệ không có lỗi", () => {
    expect(validateReportForm(valid, MOCK_SUBJECTS)).toEqual({});
  });
});

describe("normalizeBreakdown", () => {
  it("giữ breakdown cho Thanh toán", () => {
    expect(normalizeBreakdown(valid, MOCK_SUBJECTS)).toBe("DATE");
  });

  it("bỏ breakdown khi chủ đề không phải Thanh toán", () => {
    expect(normalizeBreakdown({ ...valid, subject: "VISITS" }, MOCK_SUBJECTS)).toBeUndefined();
    expect(toReportQuery({ ...valid, subject: "VISITS" }, MOCK_SUBJECTS)).toEqual({
      subject: "VISITS",
      from: "2026-10-01",
      to: "2026-10-20",
    });
  });
});
