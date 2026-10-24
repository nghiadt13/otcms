import { describe, expect, it } from "vitest";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import {
  appointmentDetailTitle,
  appointmentSentenceTime,
  appointmentWhenLabel,
  arrivalNote,
  examFeeLabel,
  patientOfLabel,
  refundNotice,
  visitHrefOf,
} from "./patientAppointmentPresentation";

// 23/10/2026 là thứ sáu theo lịch VN.
const START = "2026-10-23T08:30:00+07:00";

describe("patientAppointmentPresentation", () => {
  it("ghi thứ, ngày và giờ theo giờ Việt Nam", () => {
    expect(appointmentWhenLabel(START)).toBe("Thứ sáu, 23/10/2026 · 08:30");
    expect(appointmentDetailTitle(START)).toBe("Lịch khám ngày 23/10/2026");
    expect(appointmentSentenceTime(START)).toBe("08:30 thứ sáu 23/10/2026");
  });

  it("luôn ghi người khám kèm quan hệ", () => {
    expect(
      patientOfLabel({ id: "p", fullName: "Nguyễn Thị Lan", relationship: "PARENT" }),
    ).toBe("Nguyễn Thị Lan · Cha/Mẹ");
  });

  it("phí ghi số tiền và trạng thái bằng chữ", () => {
    expect(examFeeLabel({ amount: 200000, status: "PENDING_PAYMENT" })).toBe(
      `Phí khám ${formatCurrencyVnd(200000)} · Chưa thanh toán`,
    );
    expect(examFeeLabel({ amount: 200000, status: "PAID" })).toContain("Đã thanh toán");
  });

  it("câu không hoàn tiền (BR-53) ghi đúng số tiền đã trả", () => {
    expect(refundNotice({ amount: 200000, status: "PAID" })).toBe(
      `Phí khám ${formatCurrencyVnd(200000)} đã thanh toán trực tuyến sẽ không được hoàn lại.`,
    );
  });

  it("chỉ dẫn quầy ghi mã BN khi biết mã, không thì nói chung", () => {
    expect(arrivalNote("BN-000123")).toContain("mã bệnh nhân BN-000123");
    expect(arrivalNote(undefined)).not.toContain("BN-");
  });

  it("đường tới lượt khám chỉ có khi lịch có lượt khám", () => {
    expect(visitHrefOf(null)).toBeNull();
    expect(visitHrefOf("vis-an-today")).toBe("/portal/visits/vis-an-today");
  });
});
