import { describe, expect, it } from "vitest";
import {
  averagePerDay,
  deltaOf,
  formatCompactVnd,
  niceAxisMax,
  sharePercent,
} from "./ownerAnalyticsFormat";

describe("deltaOf", () => {
  it("không có kỳ trước thì none", () => {
    expect(deltaOf(10, null)).toEqual({ kind: "none" });
  });
  it("bằng nhau thì flat", () => {
    expect(deltaOf(5, 5)).toEqual({ kind: "flat" });
  });
  it("tăng và giảm tính đúng phần trăm", () => {
    expect(deltaOf(120, 100)).toEqual({ kind: "up", percent: 20, diff: 20 });
    expect(deltaOf(50, 100)).toEqual({ kind: "down", percent: -50, diff: -50 });
  });
  it("kỳ trước bằng 0 thì chỉ báo hướng, không chia cho 0", () => {
    expect(deltaOf(7, 0)).toEqual({ kind: "up", percent: null, diff: 7 });
  });
});

describe("định dạng và tính toán", () => {
  it("rút gọn tiền cho nhãn trục", () => {
    expect(formatCompactVnd(12_400_000)).toBe("12,4 tr ₫");
    expect(formatCompactVnd(1_250_000_000)).toBe("1,3 tỷ ₫");
    // Intl của vi-VN dùng khoảng trắng không ngắt (U+00A0) trước ký hiệu ₫.
    expect(formatCompactVnd(850_000).replace(/ /g, " ")).toBe("850.000 ₫");
  });
  it("tỷ trọng và trung bình xử lý mẫu bằng 0", () => {
    expect(sharePercent(25, 100)).toBe(25);
    expect(sharePercent(5, 0)).toBe(0);
    expect(averagePerDay(30, 10)).toBe(3);
    expect(averagePerDay(0, 0)).toBeNull();
  });
  it("mốc trục luôn không nhỏ hơn giá trị lớn nhất", () => {
    for (const max of [1, 7, 930_000, 14_200_000, 18_400_001]) {
      expect(niceAxisMax(max)).toBeGreaterThanOrEqual(max);
    }
    expect(niceAxisMax(0)).toBe(1);
    expect(niceAxisMax(14_200_000)).toBe(20_000_000);
  });
});
