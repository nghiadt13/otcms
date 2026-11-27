import { describe, expect, it } from "vitest";
import type { RefundRow } from "../types/ownerFinance";
import { filterRefunds, personKey, roleLabel } from "./ownerFinanceFormat";

function row(over: Partial<RefundRow>): RefundRow {
  return {
    itemId: "i",
    invoiceNumber: "INV-1",
    patientName: "A",
    description: "Khám",
    amount: 1,
    status: "REFUNDED",
    reason: null,
    paidAt: null,
    refundedAt: null,
    refundedByName: "Hoa",
    refundedByRole: "RECEPTIONIST",
    ...over,
  };
}

const rows = [
  row({ itemId: "1" }),
  row({ itemId: "2", refundedByName: "Nam", refundedByRole: "ADMIN" }),
  row({
    itemId: "3",
    status: "REFUND_PENDING",
    refundedByName: null,
    refundedByRole: null,
  }),
  row({ itemId: "4", refundedByName: null, refundedByRole: null }),
];
const ids = (list: RefundRow[]) => list.map((r) => r.itemId);

describe("lọc danh sách hoàn tiền", () => {
  it("không lọc thì giữ nguyên tất cả", () => {
    expect(ids(filterRefunds(rows, "ALL", null))).toEqual(["1", "2", "3", "4"]);
  });

  it("lọc theo trạng thái", () => {
    expect(ids(filterRefunds(rows, "REFUND_PENDING", null))).toEqual(["3"]);
    expect(ids(filterRefunds(rows, "REFUNDED", null))).toEqual(["1", "2", "4"]);
  });

  it("lọc theo người hoàn, khoản chờ hoàn không thuộc ai", () => {
    expect(
      ids(filterRefunds(rows, "ALL", personKey("Hoa", "RECEPTIONIST"))),
    ).toEqual(["1"]);
    expect(ids(filterRefunds(rows, "ALL", personKey(null, null)))).toEqual([
      "4",
    ]);
  });
});

describe("nhãn vai trò", () => {
  it("dịch vai trò đã biết, giữ nguyên vai trò lạ, rỗng khi không có", () => {
    expect(roleLabel("RECEPTIONIST")).toBe("Lễ tân");
    expect(roleLabel("NEW_ROLE")).toBe("NEW_ROLE");
    expect(roleLabel(null)).toBe("");
  });
});
