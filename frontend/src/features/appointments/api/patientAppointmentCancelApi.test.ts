import { beforeEach, describe, expect, it } from "vitest";
import { isApiError, type ApiError } from "@/lib/api/apiError";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { readStoredAppointments, resetBookingMock } from "./bookingMockStore";
import {
  cancelMyAppointment,
  getMyAppointment,
  listMyAppointments,
  resetPatientAppointmentMock,
} from "./patientAppointmentApi";
import { RACE_APPOINTMENT_ID } from "./patientAppointmentMockData";

// Hủy lịch của khu bệnh nhân (BR-22, BR-53): chỉ lịch Đã đặt; giờ được nhả trong kho dùng chung.
// Đồng hồ cố định: thứ bảy 24/10/2026, 09:00 giờ VN (lịch mẫu tính theo ngày này).
const NOW = new Date("2026-10-24T09:00:00+07:00");

/** Lỗi mà lớp API ném ra; test thất bại nếu lời gọi không ném. */
async function errorOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (isApiError(error)) return error;
    throw error;
  }
  throw new Error("lời gọi không ném lỗi");
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
});

describe("cancelMyAppointment", () => {
  it("hủy lịch Đã đặt: trạng thái Đã hủy, không còn thao tác, giờ được nhả trong kho dùng chung", async () => {
    const item = await cancelMyAppointment("apt-1001");
    expect(item.status).toBe("CANCELLED");
    expect(item.allowedActions).toEqual([]);

    const stored = readStoredAppointments().find((record) => record.id === "apt-1001");
    expect(stored?.status).toBe("CANCELLED");

    const upcoming = await listMyAppointments({ when: "upcoming" });
    expect(upcoming.items.map((row) => row.id)).not.toContain("apt-1001");
  });

  it("hủy lịch đã thanh toán vẫn được (phí không hoàn lại, xử lý ở giao diện)", async () => {
    const item = await cancelMyAppointment("apt-1003");
    expect(item).toMatchObject({ status: "CANCELLED", examFee: { status: "PAID" } });
  });

  it("hủy lịch riêng khu bệnh nhân (bác sĩ vắng): chuyển sang Đã qua", async () => {
    await cancelMyAppointment("apt-2101");
    const past = await listMyAppointments({ when: "past", page: 0, size: 10 });
    expect(past.items.find((row) => row.id === "apt-2101")?.status).toBe("CANCELLED");
  });

  it("lịch đã check-in → 409 MSG-32", async () => {
    expect(await errorOf(cancelMyAppointment("apt-2001"))).toMatchObject({
      status: 409,
      code: "MSG-32",
    });
  });

  it("lịch đã check-in ở quầy nhưng danh sách còn Đã đặt → 409 MSG-32 và vẫn Đã đặt", async () => {
    expect(await errorOf(cancelMyAppointment(RACE_APPOINTMENT_ID))).toMatchObject({
      status: 409,
      code: "MSG-32",
    });
    const item = await getMyAppointment(RACE_APPOINTMENT_ID);
    expect(item.status).toBe("BOOKED");
  });

  it("mã không thuộc phạm vi hoặc không có → 404 MSG-29", async () => {
    expect(await errorOf(cancelMyAppointment("apt-1004"))).toMatchObject({
      status: 404,
      code: "MSG-29",
    });
  });
});
