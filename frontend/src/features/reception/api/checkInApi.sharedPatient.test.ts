import { beforeEach, describe, expect, it } from "vitest";
import { registerMockPatient } from "@/lib/mock/sharedPatientDirectoryMock";
import { checkInPatient, collectExamFee, getCheckInContext } from "./checkInApi";
import { rejectionOf } from "./checkInTestErrors";
import { resetReceptionMockStore } from "./receptionMockStore";

// Hồ sơ tạo ở UC10 (feature patients) đăng ký vào kho giả dùng chung. Màn Tiếp nhận phải tra được
// như BE thật, không trả 404 MSG-41 khi lễ tân mở ?patientId=pat-new-N.

const walkInPatient = {
  id: "pat-new-1",
  patientCode: "BN-000203",
  fullName: "Lê Thị Cúc",
  dateOfBirth: "1990-07-01",
  sex: "FEMALE" as const,
  phone: "0987654321",
};

beforeEach(() => {
  resetReceptionMockStore();
  registerMockPatient(walkInPatient);
});

describe("bệnh nhân mới tạo ở UC10", () => {
  it("bối cảnh tiếp nhận chỉ có lựa chọn Vãng lai, không có lịch, không có thông báo", async () => {
    const context = await getCheckInContext({ patientId: "pat-new-1" });
    expect(context.patient).toEqual(walkInPatient);
    expect(context.options.map((option) => option.kind)).toEqual(["WALK_IN"]);
    expect(context.ineligible).toEqual([]);
    expect(context.notice).toBeNull();
  });

  it("thu phí vãng lai rồi tiếp nhận được, không còn 404 MSG-41", async () => {
    const payment = await collectExamFee({
      patientId: "pat-new-1",
      appointmentId: null,
      serviceId: "svc-kham-tq",
      method: "CASH",
    });
    expect(payment.status).toBe("SUCCESS");

    const result = await checkInPatient({
      patientId: "pat-new-1",
      appointmentId: null,
      visitId: null,
      serviceId: "svc-kham-tq",
      priorityGroupCode: null,
      examFeePaymentId: payment.id,
    });
    expect(result).toMatchObject({ queueType: "EXAMINATION", roomName: expect.any(String) });
  });

  it("id không có ở cả hai kho vẫn trả 404 MSG-41", async () => {
    const error = await rejectionOf(getCheckInContext({ patientId: "pat-new-99" }));
    expect(error).toMatchObject({ status: 404, code: "MSG-41" });
  });
});
