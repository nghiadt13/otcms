import { describe, expect, it } from "vitest";
import type { PaymentItem } from "../../types/patientBilling";
import {
  isPaymentSourceKind,
  historyHref,
  parseItemIds,
  paymentConfirmHref,
  pickPayableItems,
  payableSourceTitle,
  readDateParam,
  sourceDetailHref,
  sumSelectedPending,
} from "./patientBillingPresentation";

const item = (
  id: string,
  amount: number,
  status: PaymentItem["status"],
): PaymentItem => ({
  id,
  label: id,
  amount,
  status,
  cancellationReason: null,
  receipt: null,
});

describe("chọn khoản để trả", () => {
  const items = [
    item("a", 1200000, "PENDING_PAYMENT"),
    item("b", 250000, "PENDING_PAYMENT"),
    item("c", 200000, "PAID"),
  ];

  it("tổng tạm tính chỉ cộng khoản chờ thanh toán đã chọn", () => {
    expect(sumSelectedPending(items, new Set(["a", "b"]))).toBe(1450000);
    expect(sumSelectedPending(items, new Set(["a", "c"]))).toBe(1200000);
    expect(sumSelectedPending(items, new Set())).toBe(0);
  });

  it("chỉ hợp lệ khi có khoản và mọi khoản đều chờ thanh toán của nguồn", () => {
    expect(pickPayableItems(items, ["b", "a"])?.map((i) => i.id)).toEqual([
      "b",
      "a",
    ]);
    expect(pickPayableItems(items, [])).toBeNull();
    expect(pickPayableItems(items, ["a", "c"])).toBeNull();
    expect(pickPayableItems(items, ["a", "khong-co"])).toBeNull();
  });

  it("tách danh sách trong URL, bỏ phần rỗng và trùng", () => {
    expect(parseItemIds("a, b,,a")).toEqual(["a", "b"]);
    expect(parseItemIds(undefined)).toEqual([]);
  });
});

describe("đường dẫn và nhãn", () => {
  it("tiêu đề nguồn ghi loại, ngày, người khám và quan hệ", () => {
    expect(
      payableSourceTitle({
        kind: "visit",
        date: "2026-10-23",
        patient: { id: "p", fullName: "Nguyễn Văn An", relationship: "SELF" },
      }),
    ).toBe("Lượt khám 23/10/2026 · Nguyễn Văn An (Bản thân)");
    expect(
      payableSourceTitle({
        kind: "appointment",
        date: "2026-10-27T14:00:00+07:00",
        patient: {
          id: "p",
          fullName: "Nguyễn Thị Lan",
          relationship: "PARENT",
        },
      }),
    ).toBe("Lịch hẹn 27/10/2026 14:00 · Nguyễn Thị Lan (Cha/Mẹ)");
  });

  it("đường dẫn trang xác nhận mang nguồn, mã và danh sách khoản", () => {
    expect(
      paymentConfirmHref("visit", "vis-an-today", ["svc-pending", "svc-cbc"]),
    ).toBe(
      "/portal/payments/pay?source=visit&id=vis-an-today&items=svc-pending%2Csvc-cbc",
    );
    expect(sourceDetailHref("appointment", "apt-lan-27")).toBe(
      "/portal/payments/appointment/apt-lan-27",
    );
  });

  it("lọc lịch sử bỏ tham số rỗng", () => {
    expect(
      historyHref({ from: "2026-10-01", to: "", profileId: undefined }),
    ).toBe("/portal/payments/history?from=2026-10-01");
    expect(historyHref({})).toBe("/portal/payments/history");
  });

  it("nhận biết loại nguồn và ngày hợp lệ từ URL", () => {
    expect(isPaymentSourceKind("visit")).toBe(true);
    expect(isPaymentSourceKind("appointment")).toBe(true);
    expect(isPaymentSourceKind("history")).toBe(false);
    expect(readDateParam("2026-10-01")).toBe("2026-10-01");
    expect(readDateParam("01/10/2026")).toBeUndefined();
    expect(readDateParam(["2026-10-01"])).toBeUndefined();
  });
});
