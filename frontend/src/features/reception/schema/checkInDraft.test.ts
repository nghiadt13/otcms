import { describe, expect, it } from "vitest";
import type { CheckInOption, ExamFeePayment } from "../types/checkIn";
import {
  buildCheckInRequest,
  buildExamFeeRequest,
  defaultOptionKey,
  examFeeView,
  findOption,
  optionKey,
} from "./checkInDraft";

const appointment: CheckInOption = {
  kind: "APPOINTMENT",
  appointmentId: "apt-1001",
  startAt: "2026-10-24T08:30:00+07:00",
  serviceName: "Khám chỉnh hình tổng quát",
  visitType: "FOLLOW_UP",
  doctorName: "BS. Phạm Thu Hà",
  roomName: "Phòng khám 2",
  examFee: { amount: 200000, status: "PENDING_PAYMENT", paidOnline: false },
};
const walkIn: CheckInOption = {
  kind: "WALK_IN",
  services: [
    { id: "svc-kham-tq", name: "Khám chỉnh hình tổng quát", price: 200000 },
    { id: "svc-kham-cs", name: "Khám cột sống", price: 250000 },
  ],
};
const revisit: CheckInOption = {
  kind: "RETURN_VISIT",
  visitId: "vis-6101",
  firstCheckInDate: "2026-10-21",
  dayOfPeriod: 4,
  nextQueue: { type: "RESULT_REVIEW", roomName: "Phòng khám 2", doctorName: "BS. Phạm Thu Hà" },
  examFee: { amount: 0, status: "NOT_REQUIRED", paidOnline: false },
};

const cashPaid: ExamFeePayment = {
  id: "pay-5001",
  method: "CASH",
  amount: 200000,
  status: "SUCCESS",
  receiptNo: "PT-20261024-0042",
  vnpay: null,
};

describe("khóa và mặc định của lựa chọn", () => {
  it("khóa lựa chọn tách theo loại và định danh", () => {
    expect(optionKey(appointment)).toBe("APPOINTMENT:apt-1001");
    expect(optionKey(revisit)).toBe("RETURN_VISIT:vis-6101");
    expect(optionKey(walkIn)).toBe("WALK_IN");
    expect(findOption([appointment, walkIn], "WALK_IN")).toBe(walkIn);
    expect(findOption([appointment], "APPOINTMENT:apt-9999")).toBeNull();
  });

  it("mặc định là lịch hẹn đang mở từ danh sách, không thì lựa chọn đầu tiên", () => {
    expect(defaultOptionKey([walkIn, appointment], { kind: "appointment", id: "apt-1001" })).toBe(
      "APPOINTMENT:apt-1001",
    );
    expect(defaultOptionKey([appointment, walkIn], { kind: "patient", id: "pat-an" })).toBe(
      "APPOINTMENT:apt-1001",
    );
    expect(defaultOptionKey([], { kind: "patient", id: "pat-em" })).toBeNull();
  });
});

describe("phí đang áp dụng", () => {
  it("lịch hẹn lấy phí của lịch; khoản đã thu trong màn làm phí thành đã trả", () => {
    expect(examFeeView(appointment, null, null)).toEqual({
      kind: "FEE",
      status: "PENDING_PAYMENT",
      amount: 200000,
      paidOnline: false,
    });
    expect(examFeeView(appointment, null, cashPaid)).toMatchObject({ status: "PAID" });
  });

  it("vãng lai chưa chọn dịch vụ: chưa có phí; chọn rồi thì theo giá dịch vụ", () => {
    expect(examFeeView(walkIn, null, null)).toEqual({ kind: "NEEDS_SERVICE" });
    expect(examFeeView(walkIn, "svc-kham-cs", null)).toMatchObject({
      status: "PENDING_PAYMENT",
      amount: 250000,
    });
  });

  it("quay lại trong 7 ngày: không thu, số tiền 0", () => {
    expect(examFeeView(revisit, null, null)).toEqual({
      kind: "FEE",
      status: "NOT_REQUIRED",
      amount: 0,
      paidOnline: false,
    });
  });
});

describe("dựng body gửi BE", () => {
  it("thu phí lịch hẹn gửi appointmentId, vãng lai gửi serviceId", () => {
    expect(buildExamFeeRequest("pat-an", appointment, null, "CASH")).toEqual({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      serviceId: null,
      method: "CASH",
    });
    expect(buildExamFeeRequest("pat-moi", walkIn, "svc-kham-tq", "VNPAY")).toEqual({
      patientId: "pat-moi",
      appointmentId: null,
      serviceId: "svc-kham-tq",
      method: "VNPAY",
    });
  });

  it("tiếp nhận: đúng một trường theo lựa chọn, gắn mã thanh toán chỉ khi đã thu", () => {
    expect(
      buildCheckInRequest({
        patientId: "pat-an",
        option: appointment,
        serviceId: null,
        priorityGroupCode: null,
        payment: cashPaid,
      }),
    ).toEqual({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      visitId: null,
      serviceId: null,
      priorityGroupCode: null,
      examFeePaymentId: "pay-5001",
    });
    expect(
      buildCheckInRequest({
        patientId: "pat-cuong",
        option: revisit,
        serviceId: null,
        priorityGroupCode: null,
        payment: null,
      }),
    ).toMatchObject({ appointmentId: null, visitId: "vis-6101", serviceId: null, examFeePaymentId: null });
  });
});
