import { describe, expect, it } from "vitest";
import { checkInReadiness, describeMissing } from "./checkInReadiness";

// BR-26: nút Tiếp nhận chỉ bật khi đủ: có lựa chọn, dịch vụ (vãng lai), phí đã trả/không thu, có mặt.
describe("checkInReadiness", () => {
  it("chưa có lựa chọn nào: chỉ thiếu cách tiếp nhận", () => {
    expect(
      checkInReadiness({ optionKind: null, serviceChosen: false, feeStatus: null, present: false }),
    ).toEqual(["OPTION"]);
  });

  it("thiếu xác nhận có mặt: không đủ, dù phí đã trả", () => {
    const missing = checkInReadiness({
      optionKind: "APPOINTMENT",
      serviceChosen: false,
      feeStatus: "PAID",
      present: false,
    });
    expect(missing).toEqual(["PRESENT"]);
    expect(describeMissing(missing)).toBe("Còn thiếu: xác nhận có mặt");
  });

  it("phí chưa trả và chưa có mặt: nêu cả hai theo thứ tự trên màn", () => {
    const missing = checkInReadiness({
      optionKind: "APPOINTMENT",
      serviceChosen: false,
      feeStatus: "PENDING_PAYMENT",
      present: false,
    });
    expect(missing).toEqual(["FEE", "PRESENT"]);
    expect(describeMissing(missing)).toBe("Còn thiếu: thu phí khám, xác nhận có mặt");
  });

  it("đã có mặt và phí đã trả: đủ điều kiện", () => {
    expect(
      checkInReadiness({ optionKind: "APPOINTMENT", serviceChosen: false, feeStatus: "PAID", present: true }),
    ).toEqual([]);
  });

  it("quay lại trong 7 ngày không thu phí: không cần phí, chỉ cần có mặt", () => {
    expect(
      checkInReadiness({ optionKind: "RETURN_VISIT", serviceChosen: false, feeStatus: "NOT_REQUIRED", present: true }),
    ).toEqual([]);
  });

  it("vãng lai chưa chọn dịch vụ: thiếu dịch vụ, chưa tính phí", () => {
    const missing = checkInReadiness({
      optionKind: "WALK_IN",
      serviceChosen: false,
      feeStatus: null,
      present: false,
    });
    expect(missing).toEqual(["SERVICE", "PRESENT"]);
    expect(describeMissing(missing)).toBe("Còn thiếu: chọn dịch vụ khám, xác nhận có mặt");
  });

  it("vãng lai đã chọn dịch vụ nhưng chưa thu: thiếu phí", () => {
    expect(
      checkInReadiness({ optionKind: "WALK_IN", serviceChosen: true, feeStatus: "PENDING_PAYMENT", present: true }),
    ).toEqual(["FEE"]);
  });

  it("phí không xác định (null) với lịch hẹn: coi như chưa trả, không bật nút", () => {
    expect(
      checkInReadiness({ optionKind: "APPOINTMENT", serviceChosen: false, feeStatus: null, present: true }),
    ).toEqual(["FEE"]);
  });
});
