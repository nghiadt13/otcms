import { describe, expect, it } from "vitest";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { describeComparison, formatMetricValue } from "./describeComparison";

describe("describeComparison", () => {
  it("tăng kèm phần trăm", () => {
    expect(
      describeComparison({ value: 42, previousValue: 38, format: "count" }),
    ).toBe("tăng 4 so với kỳ trước 38 (+10,5%)");
  });

  it("giảm có dấu trừ thật", () => {
    expect(
      describeComparison({ value: 38, previousValue: 41, format: "count" }),
    ).toBe("giảm 3 so với kỳ trước 41 (−7,3%)");
  });

  it("không đổi", () => {
    expect(
      describeComparison({ value: 2, previousValue: 2, format: "count" }),
    ).toBe("không đổi so với kỳ trước 2");
  });

  it("kỳ trước bằng 0 thì không có phần trăm", () => {
    expect(
      describeComparison({ value: 5, previousValue: 0, format: "count" }),
    ).toBe("tăng 5 so với kỳ trước 0");
  });

  it("thiếu số để so thì nói rõ", () => {
    expect(
      describeComparison({ value: 5, previousValue: null, format: "count" }),
    ).toBe("Không so sánh được");
    expect(
      describeComparison({ value: null, previousValue: 4, format: "rating" }),
    ).toBe("Không so sánh được");
  });

  it("số tức thời không so sánh", () => {
    expect(
      describeComparison({
        value: 3,
        previousValue: null,
        format: "count",
        snapshot: true,
      }),
    ).toBe("Số hiện tại");
  });

  it("tiền VND và điểm có định dạng riêng", () => {
    expect(
      describeComparison({
        value: 12450000,
        previousValue: 9800000,
        format: "vnd",
      }),
    ).toBe(
      `tăng ${formatCurrencyVnd(2650000)} so với kỳ trước ${formatCurrencyVnd(9800000)} (+27,0%)`,
    );
    expect(
      describeComparison({ value: 4.6, previousValue: 4.4, format: "rating" }),
    ).toBe("tăng 0,2 so với kỳ trước 4,4");
  });

  it("formatMetricValue: null là gạch, số 0 vẫn là 0", () => {
    expect(formatMetricValue(null, "count")).toBe("—");
    expect(formatMetricValue(0, "count")).toBe("0");
    expect(formatMetricValue(1250, "count")).toBe("1.250");
    expect(formatMetricValue(4.6, "rating")).toBe("4,6");
  });
});
