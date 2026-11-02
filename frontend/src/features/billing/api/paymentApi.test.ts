import { describe, expect, it } from "vitest";
import type { PaymentListQuery } from "../types/payment";
import * as paymentApi from "./paymentApi";
import { getPaymentDetail, listPayments } from "./paymentApi";
import {
  PAYMENT_ERROR_ID,
  PAYMENT_FORBIDDEN_ID,
  PAYMENT_SEED,
} from "./paymentMockData";

const DEFAULT_RANGE: PaymentListQuery = {
  from: "2026-09-20",
  to: "2026-10-20",
  q: "",
  method: "",
};

describe("listPayments", () => {
  it("kỳ mặc định: 44 khoản, 3 trang, trang 0 có 20 dòng, paidAt giảm dần", async () => {
    const page = await listPayments(DEFAULT_RANGE);
    expect(page.totalItems).toBe(44);
    expect(page.totalPages).toBe(3);
    expect(page.items).toHaveLength(20);
    const times = page.items.map((item) => item.paidAt);
    expect(times).toEqual([...times].sort().reverse());
    expect(page.items[0].allowedActions).toEqual(["view"]);
  });

  it("summary tính trên toàn tập lọc, không theo trang; tổng = tiền mặt + VNPay", async () => {
    const inRange = PAYMENT_SEED.filter(
      (p) => p.paidAt.slice(0, 10) >= "2026-09-20",
    );
    const expectedTotal = inRange.reduce((sum, p) => sum + p.amount, 0);
    const first = await listPayments({ ...DEFAULT_RANGE, page: 0 });
    const last = await listPayments({ ...DEFAULT_RANGE, page: 2 });
    expect(last.items).toHaveLength(4);
    for (const page of [first, last]) {
      expect(page.summary.totalAmount).toBe(expectedTotal);
      expect(page.summary.totalAmount).toBe(
        page.summary.cashAmount + page.summary.vnpayAmount,
      );
    }
  });

  it("lọc phương thức: phương thức kia trong summary bằng 0", async () => {
    const cash = await listPayments({
      ...DEFAULT_RANGE,
      method: "CASH",
      size: 100,
    });
    expect(cash.items.every((item) => item.method === "CASH")).toBe(true);
    expect(cash.summary.vnpayAmount).toBe(0);
    expect(cash.summary.cashAmount).toBeGreaterThan(0);
    const vnpay = await listPayments({
      ...DEFAULT_RANGE,
      method: "VNPAY",
      size: 100,
    });
    expect(vnpay.items.every((item) => item.cashierName === null)).toBe(true);
    expect(vnpay.summary.cashAmount).toBe(0);
  });

  it("tìm theo tên không phân biệt hoa thường và theo số phiếu thu", async () => {
    const byName = await listPayments({
      ...DEFAULT_RANGE,
      q: "  lê THỊ bích ",
    });
    expect(byName.totalItems).toBe(2);
    const byReceipt = await listPayments({
      ...DEFAULT_RANGE,
      q: "PT-2026-000481",
    });
    expect(byReceipt.items.map((item) => item.receiptNumber)).toEqual([
      "PT-2026-000481",
    ]);
  });

  it("khoảng ngày gồm cả hai đầu và loại khoản ngoài kỳ", async () => {
    const outside = await listPayments({
      ...DEFAULT_RANGE,
      from: "2026-09-10",
      to: "2026-09-10",
    });
    expect(outside.totalItems).toBe(2);
    const sameDay = await listPayments({
      ...DEFAULT_RANGE,
      from: "2026-10-20",
      to: "2026-10-20",
    });
    expect(
      sameDay.items.every((item) => item.paidAt.startsWith("2026-10-20")),
    ).toBe(true);
  });

  it("không khớp hoặc kỳ trống: items rỗng, summary 0", async () => {
    for (const query of [
      { ...DEFAULT_RANGE, q: "zzz" },
      { ...DEFAULT_RANGE, from: "2026-01-01", to: "2026-01-31" },
    ]) {
      const page = await listPayments(query);
      expect(page.items).toEqual([]);
      expect(page.summary).toEqual({
        totalAmount: 0,
        cashAmount: 0,
        vnpayAmount: 0,
      });
    }
  });

  it("từ > đến: 400 MSG-54 gắn vào field to", async () => {
    await expect(
      listPayments({ ...DEFAULT_RANGE, from: "2026-10-20", to: "2026-10-01" }),
    ).rejects.toMatchObject({
      status: 400,
      code: "MSG-54",
      fieldErrors: [{ field: "to", code: "MSG-54" }],
    });
  });

  it("q = ERR-500: 500 MSG-04", async () => {
    await expect(
      listPayments({ ...DEFAULT_RANGE, q: "ERR-500" }),
    ).rejects.toMatchObject({ status: 500, code: "MSG-04" });
  });
});

describe("getPaymentDetail", () => {
  it("Σ lines.price = amount, chỉ có hành động view, không có itemSummary", async () => {
    for (const seed of [PAYMENT_SEED[0], PAYMENT_SEED[2], PAYMENT_SEED[3]]) {
      const detail = await getPaymentDetail(seed.id);
      expect(detail.lines.reduce((sum, line) => sum + line.price, 0)).toBe(
        detail.amount,
      );
      expect(detail.allowedActions).toEqual(["view"]);
      expect(detail).not.toHaveProperty("itemSummary");
    }
  });

  it("ca bắt buộc: VNPay chỉ phí khám có mã giao dịch; tiền mặt 6 dòng", async () => {
    const vnpay = await getPaymentDetail(PAYMENT_SEED[1].id);
    expect(vnpay.method).toBe("VNPAY");
    expect(vnpay.lines.map((line) => line.name)).toEqual(["Phí khám"]);
    expect(vnpay.vnpayTransactionCode).toBe("14872035");
    expect(vnpay.cashierName).toBeNull();
    const long = await getPaymentDetail(PAYMENT_SEED[2].id);
    expect(long.lines).toHaveLength(6);
    expect(long.vnpayTransactionCode).toBeNull();
  });

  it("404 không có, 403 ngoài quyền, 500 lỗi dịch vụ", async () => {
    await expect(
      getPaymentDetail("00000000-0000-4000-8000-000000000001"),
    ).rejects.toMatchObject({ status: 404, code: "MSG-50" });
    await expect(getPaymentDetail(PAYMENT_FORBIDDEN_ID)).rejects.toMatchObject({
      status: 403,
      code: "MSG-50",
    });
    await expect(getPaymentDetail(PAYMENT_ERROR_ID)).rejects.toMatchObject({
      status: 500,
      code: "MSG-04",
    });
  });
});

it("paymentApi chỉ export hàm đọc", () => {
  expect(Object.keys(paymentApi).sort()).toEqual([
    "PAYMENT_ERROR_QUERY",
    "getPaymentDetail",
    "listPayments",
  ]);
});
